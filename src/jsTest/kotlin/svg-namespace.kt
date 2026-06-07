package kotlinx.html.tests

import web.dom.document
import kotlinx.html.dom.create
import kotlinx.html.js.svg
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for https://github.com/Kotlin/kotlinx.html/issues/285
 *
 * `Tag.namespace` drives whether the JS DOM builder calls `createElementNS` (SVG) or
 * `createElement` (HTML). The original report was that Kotlin/JS mangled the `namespace`
 * property name, so the builder never saw the SVG namespace and the element was created in the
 * HTML namespace (SVGs failed to render). This test pins the behaviour: an `svg { }` element must
 * land in the SVG namespace.
 */
class SvgNamespaceTest {
    @Test
    fun svg_element_is_created_in_the_svg_namespace() {
        val el = document.create.svg("icon") {}
        assertEquals("http://www.w3.org/2000/svg", el.namespaceURI)
        assertEquals("svg", el.tagName.lowercase())
    }
}
