package kotlinx.html.tests

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for:
 *  - https://github.com/Kotlin/kotlinx.html/issues/283 (TR shouldn't require a TBODY receiver)
 *  - https://github.com/Kotlin/kotlinx.html/issues/284 (TBODY should be a FlowContent)
 *
 * The fork adds `tbody`, `thead`, `tfoot` and `tr` to the `flowContent` group. That makes those
 * tags usable as standalone fragments (e.g. for HTMX partial swaps) and lets a single
 * `FlowContent`-receiver component render rows whether it is invoked inside a real `<tbody>` or at
 * an arbitrary flow-content root. The strict `table { tbody { tr { } } }` nesting is unchanged.
 */
class TableFragmentTest {

    /** A reusable component with a single `FlowContent` receiver — the crux of #284. */
    private fun FlowContent.renderRows(cells: List<String>) {
        cells.forEach { value ->
            tr { td { +value } }
        }
    }

    @Test
    fun tr_can_be_generated_inside_arbitrary_flow_content() {
        // #283: a bare <tr> fragment built from a FlowContent context (no enclosing <table>).
        val html = createHTML(prettyPrint = false).div {
            tr { td { +"a" } }
        }
        assertEquals("<div><tr><td>a</td></tr></div>", html)
    }

    @Test
    fun tbody_fragment_can_be_generated_standalone() {
        // #284: <tbody> as a flow-content fragment (HTMX swap target contents).
        val html = createHTML(prettyPrint = false).div {
            tbody {
                tr { td { +"x" } }
            }
        }
        assertEquals("<div><tbody><tr><td>x</td></tr></tbody></div>", html)
    }

    @Test
    fun same_component_renders_inside_a_real_tbody() {
        // renderRows has a FlowContent receiver; TBODY is now a FlowContent, so this compiles.
        val html = createHTML(prettyPrint = false).table {
            tbody {
                renderRows(listOf("a", "b"))
            }
        }
        assertEquals(
            "<table><tbody><tr><td>a</td></tr><tr><td>b</td></tr></tbody></table>",
            html
        )
    }

    @Test
    fun same_component_renders_as_a_rows_only_fragment() {
        // The HTMX "rows only" case: the very same renderRows invoked at a flow-content root.
        val html = createHTML(prettyPrint = false).div {
            renderRows(listOf("a", "b"))
        }
        assertEquals(
            "<div><tr><td>a</td></tr><tr><td>b</td></tr></div>",
            html
        )
    }

    @Test
    fun strict_table_nesting_still_works() {
        // Additive change: the original strict receivers are untouched.
        val html = createHTML(prettyPrint = false).table {
            thead { tr { th { +"H" } } }
            tbody { tr { td { +"D" } } }
        }
        assertEquals(
            "<table><thead><tr><th>H</th></tr></thead><tbody><tr><td>D</td></tr></tbody></table>",
            html
        )
    }
}
