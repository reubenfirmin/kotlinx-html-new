package kotlinx.html.tests

import web.dom.document
import web.dom.Element
import web.dom.ElementId
import web.html.HTMLDivElement
import web.html.HTMLElement
import kotlinx.html.Entities
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.consumers.trace
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.dom.prepend
import kotlinx.html.id
import kotlinx.html.js.col
import kotlinx.html.js.colGroup
import kotlinx.html.js.div
import kotlinx.html.js.form
import kotlinx.html.js.h1
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onSubmitFunction
import kotlinx.html.js.p
import kotlinx.html.js.span
import kotlinx.html.js.svg
import kotlinx.html.js.td
import kotlinx.html.js.th
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DomTreeImplTest {
    @Test fun simpleTree() {
        val node = document.body!!.append.div {
            p {
                +"test"
            }
        }

        assertEquals("DIV", node.tagName)
        assertEquals(1, node.childNodes.length)
        assertTrue(document.body!!.children.length > 0)
    }

    @Test fun appendSingleNode() {
        val myDiv = document.body!!.append.div {
            p {
                +"test"
            }
        }

        assertEquals("DIV", myDiv.tagName)
        assertEquals(document.body, myDiv.parentNode)
    }

    @Test fun appendNodeWithEventHandler() {
        var clicked = false

        val divElement = document.body!!.append.div {
            onClickFunction = {
                clicked = true
            }
        } as HTMLElement

        // Trigger click directly on the created element
        divElement.click()

        assertTrue(clicked)
    }

    @Test fun testAtMainPage() {
        document.body!!.append.div {
            id = "container-test-wasm"
        }

        val myDiv = document.create.div("panel") {
            p {
                +"Here is "
                a("http://kotlinlang.org") { +"official Kotlin site" }
            }
        }

        val container = document.getElementById(ElementId("container-test-wasm"))
        if (container == null) {
            fail("container not found")
        }

        container.appendChild(myDiv)

        assertEquals("<div class=\"panel\"><p>Here is <a href=\"http://kotlinlang.org\">official Kotlin site</a></p></div>", (container as HTMLElement).innerHTML.toString())
    }

    @Test fun appendMultipleNodes() {
        val wrapper = wrapper()

        val nodes = wrapper.append {
            div {
                +"div1"
            }
            div {
                +"div2"
            }
        }

        assertEquals(2, nodes.size)
        assertEquals("<div>div1</div><div>div2</div>", (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun appendEntity() {
        val wrapper = wrapper()
        wrapper.append.span {
            +Entities.nbsp
        }

        assertEquals("<span>&nbsp;</span>", (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun pastTagAttributeChangedShouldBeProhibited() {
        try {
            document.body!!.append.trace().div {
                p {
                    span {
                        this@div.id = "d1"
                    }
                }
            }

            fail("We shouldn't be able to modify attribute for outer tag")
        } catch (expected: Throwable) {
            assertTrue(true)
        }
    }

    @Test fun buildBiggerPage() {
        val wrapper = wrapper()

        wrapper.append {
            h1 {
                +"kotlin"
            }
            p {
                +"Here we are"
            }
            div {
                classes = setOf("root")

                div {
                    classes = setOf("menu")

                    ul {
                        li { +"item1" }
                        li { +"item2" }
                        li { +"item3" }
                    }
                }
                div {
                    classes = setOf("content")
                }
            }
        }

        assertEquals("""
                <h1>kotlin</h1>
                <p>Here we are</p>
                <div class="root">
                <div class="menu">
                <ul>
                <li>item1</li>
                <li>item2</li>
                <li>item3</li>
                </ul>
                </div>
                <div class="content"></div>
                </div>""".trimLines(), (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun testAppendAndRemoveClass() {
        val wrapper = wrapper()

        wrapper.append {
            span("class1") {
                classes += "class2"
                classes -= "class1"
            }
        }

        assertEquals("<span class=\"class2\"></span>", (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun testSvg() {
        val wrapper = wrapper()

        wrapper.append.svg {
        }

        // Find SVG element and verify namespace
        val svgElements = wrapper.getElementsByTagName("svg")
        assertTrue(svgElements.length > 0)
        val svgElement = svgElements.item(0)
        assertNotNull(svgElement)
        assertEquals("http://www.w3.org/2000/svg", svgElement.namespaceURI)
    }

    @Test fun testTdThColColGroupCreation() {
        val td = document.create.td()
        val th = document.create.th()
        val col = document.create.col()
        val colGroup = document.create.colGroup()

        assertEquals("TH", th.tagName.uppercase())
        assertEquals("TD", td.tagName.uppercase())
        assertEquals("COL", col.tagName.uppercase())
        assertEquals("COLGROUP", colGroup.tagName.uppercase())
    }

    @Test fun testPrepend() {
        val wrapper = wrapper()
        wrapper.appendChild(document.createElement("A").apply { textContent = "aaa" })

        val pElement: Element
        wrapper.prepend {
            pElement = p {
                text("OK")
            }
        }

        assertEquals("OK", pElement.textContent)
        assertEquals("<p>OK</p><a>aaa</a>", (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun testAppend() {
        val wrapper = wrapper()
        wrapper.appendChild(document.createElement("A").apply { textContent = "aaa" })

        val pElement: Element
        wrapper.append {
            pElement = p {
                text("OK")
            }
        }

        assertEquals("OK", pElement.textContent)
        assertEquals("<a>aaa</a><p>OK</p>", (wrapper as HTMLElement).innerHTML.toString())
    }

    @Test fun testComment() {
        val wrapper = wrapper()
        wrapper.append.div {
            comment("commented")
        }

        assertEquals("<div><!--commented--></div>", (wrapper as HTMLElement).innerHTML.toString())
    }

    private fun wrapper() = document.body!!.append.div {}
    private fun String.trimLines() = trimIndent().lines().filter { it.isNotBlank() }.joinToString("")
}
