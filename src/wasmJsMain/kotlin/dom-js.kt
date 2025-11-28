package kotlinx.html.dom

import kotlinx.html.DefaultUnsafe
import kotlinx.html.Entities
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.consumers.onFinalize
import web.events.Event
import web.events.EventType
import web.events.addEventListener
import web.dom.Document
import web.dom.Element
import web.dom.Node
import web.html.HTMLElement
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// wasmJs requires JS interop functions for innerHTML access
@Suppress("UNUSED_PARAMETER")
private fun setInnerHtml(element: HTMLElement, html: String): Unit =
    js("element.innerHTML = html")

@Suppress("UNUSED_PARAMETER")
private fun insertAdjacentHtml(element: Element, position: String, html: String): Unit =
    js("element.insertAdjacentHTML(position, html)")

private fun Element.setEvent(name: String, callback: (Event) -> Unit) {
    val eventName = name.removePrefix("on")
    addEventListener(EventType(eventName), callback)
}

class JSDOMBuilder<out R : Element>(val document: Document) : TagConsumer<R> {
    private val path = arrayListOf<Element>()
    private var lastLeaved: Element? = null

    override fun onTagStart(tag: Tag) {
        val namespace = tag.namespace
        val element: Element =
            if (namespace != null) {
                document.createElementNS(namespace, tag.tagName)
            } else {
                document.createElement(tag.tagName)
            }

        tag.attributesEntries.forEach {
            element.setAttribute(it.key, it.value)
        }

        if (path.isNotEmpty()) {
            path.last().appendChild(element)
        }

        path.add(element)
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        when {
            path.isEmpty() -> throw IllegalStateException("No current tag")
            path.last().tagName.lowercase() != tag.tagName.lowercase() -> throw IllegalStateException("Wrong current tag")
            else -> path.last().let { node ->
                if (value == null) {
                    node.removeAttribute(attribute)
                } else {
                    node.setAttribute(attribute, value)
                }
            }
        }
    }

    override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
        when {
            path.isEmpty() -> throw IllegalStateException("No current tag")
            path.last().tagName.lowercase() != tag.tagName.lowercase() -> throw IllegalStateException("Wrong current tag")
            else -> path.last().setEvent(event, value)
        }
    }

    override fun onTagEnd(tag: Tag) {
        if (path.isEmpty() || path.last().tagName.lowercase() != tag.tagName.lowercase()) {
            throw IllegalStateException("We haven't entered tag ${tag.tagName} but trying to leave")
        }

        lastLeaved = path.removeAt(path.lastIndex)
    }

    override fun onTagContent(content: CharSequence) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current DOM node")
        }

        path.last().appendChild(document.createTextNode(content.toString()))
    }

    override fun onTagContentEntity(entity: Entities) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current DOM node")
        }

        // Use a temporary element to let the browser decode HTML entities
        val s = document.createElement("span") as HTMLElement
        setInnerHtml(s, entity.text)
        val childNodes = s.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes[i]
            if (node.nodeType == Node.TEXT_NODE) {
                path.last().appendChild(node)
                break
            }
        }
    }

    override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
        with(DefaultUnsafe()) {
            block()
            // insertAdjacentHTML is the proper API for injecting raw HTML
            insertAdjacentHtml(path.last(), "beforeend", toString())
        }
    }


    override fun onTagComment(content: CharSequence) {
        if (path.isEmpty()) {
            throw IllegalStateException("No current DOM node")
        }

        path.last().appendChild(document.createComment(content.toString()))
    }

    @Suppress("UNCHECKED_CAST")
    override fun finalize(): R =
        lastLeaved as? R ?: throw IllegalStateException("We can't finalize as there was no tags")
}

fun Document.createTree(): TagConsumer<Element> = JSDOMBuilder(this)
val Document.create: TagConsumer<Element>
    get() = JSDOMBuilder(this)

@OptIn(ExperimentalContracts::class)
fun Node.append(block: TagConsumer<Element>.() -> Unit): List<Element> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return buildList {
        ownerDocumentExt.createTree().onFinalize { it, partial ->
            if (!partial) {
                add(it)
                appendChild(it)
            }
        }.block()
    }
}

@OptIn(ExperimentalContracts::class)
fun Node.prepend(block: TagConsumer<Element>.() -> Unit): List<Element> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return buildList {
        ownerDocumentExt.createTree().onFinalize { it, partial ->
            if (!partial) {
                add(it)
                insertBefore(it, firstChild)
            }
        }.block()
    }
}

val Element.append: TagConsumer<Element>
    get() = ownerDocumentExt.createTree().onFinalize { element, partial ->
        if (!partial) {
            this@append.appendChild(element)
        }
    }

val Element.prepend: TagConsumer<Element>
    get() = ownerDocumentExt.createTree().onFinalize { element, partial ->
        if (!partial) {
            this@prepend.insertBefore(element, this@prepend.firstChild)
        }
    }

private val Node.ownerDocumentExt: Document
    get() = when {
        this is Document -> this
        else -> ownerDocument ?: throw IllegalStateException("Node has no ownerDocument")
    }
