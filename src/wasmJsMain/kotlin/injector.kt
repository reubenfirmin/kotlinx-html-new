package kotlinx.html.injector

import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.dom.append
import web.dom.Element
import kotlin.reflect.KMutableProperty1

private fun classListToList(element: Element): List<String> {
    val classList = element.classList
    return (0 until classList.length).map { classList.item(it)?.toString() ?: "" }
}

fun <F : Any, T : Any> F.injectTo(bean: T, field: KMutableProperty1<T, in F>) {
    field.set(bean, this)
}

@Suppress("UNCHECKED_CAST")
private fun <F : Any, T : Any> F.injectToUnsafe(bean: T, field: KMutableProperty1<T, out F>) {
    injectTo(bean, field as KMutableProperty1<T, in F>)
}

interface InjectCapture
class InjectByClassName(val className: String) : InjectCapture
class InjectByTagName(val tagName: String) : InjectCapture
object InjectRoot : InjectCapture
interface CustomCapture : InjectCapture {
    fun apply(element: Element): Boolean
}

class InjectorConsumer<out T : Any>(
    val downstream: TagConsumer<Element>,
    val bean: T,
    rules: List<Pair<InjectCapture, KMutableProperty1<T, out Element>>>,
) : TagConsumer<Element> by downstream {

    private val classesMap: Map<String, List<KMutableProperty1<T, out Element>>> = rules
        .filter { it.first is InjectByClassName }
        .map { it.first as InjectByClassName to it.second }
        .groupBy({ it.first.className }, { it.second })

    private val tagNamesMap = rules
        .filter { it.first is InjectByTagName }
        .map { it.first as InjectByTagName to it.second }
        .groupBy({ it.first.tagName.lowercase() }, { it.second })

    private val rootCaptures = rules.filter { it.first == InjectRoot }.map { it.second }
    private val customCaptures =
        rules.filter { it.first is CustomCapture }.map { it.first as CustomCapture to it.second }

    override fun onTagEnd(tag: Tag) {
        downstream.onTagEnd(tag)

        val node = downstream.finalize()

        if (classesMap.isNotEmpty()) {
            classListToList(node).flatMap { clazz ->
                classesMap[clazz] ?: emptyList()
            }.forEach { field ->
                node.injectToUnsafe(bean, field)
            }
        }

        if (tagNamesMap.isNotEmpty()) {
            tagNamesMap[node.tagName.lowercase()]?.forEach { field ->
                node.injectToUnsafe(bean, field)
            }
        }

        customCaptures.filter { it.first.apply(node) }.map { it.second }.forEach { field ->
            node.injectToUnsafe(bean, field)
        }
    }

    override fun finalize(): Element {
        val node = downstream.finalize()
        rootCaptures.forEach { field ->
            node.injectToUnsafe(bean, field)
        }

        return node
    }
}

fun <T : Any> TagConsumer<Element>.inject(
    bean: T,
    rules: List<Pair<InjectCapture, KMutableProperty1<T, out Element>>>,
): TagConsumer<Element> = InjectorConsumer(this, bean, rules)

fun <T : Any> Element.appendAndInject(
    bean: T,
    rules: List<Pair<InjectCapture, KMutableProperty1<T, out Element>>>,
    block: TagConsumer<Element>.() -> Unit,
): List<Element> = append {
    InjectorConsumer(this@append, bean, rules).block()
    Unit
}
