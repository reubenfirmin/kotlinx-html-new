package kotlinx.html.tests

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for https://github.com/Kotlin/kotlinx.html/issues/246
 *
 * `<th>` accepts flow content (e.g. `<div>`) in HTML5, exactly like `<td>`. Before the fork
 * change, `TH` implemented `HtmlInlineTag` (phrasing content only), so `th { div { } }` would
 * not compile. `TH` now implements `HtmlBlockTag` (flow content).
 */
class ThFlowContentTest {
    @Test
    fun th_allows_a_div_child() {
        val html = createHTML(prettyPrint = false).table {
            thead {
                tr {
                    th {
                        div(classes = "wrapper") { +"Header" }
                    }
                }
            }
        }

        assertEquals(
            "<table><thead><tr><th><div class=\"wrapper\">Header</div></th></tr></thead></table>",
            html
        )
    }

    @Test
    fun th_still_allows_plain_phrasing_content() {
        val html = createHTML(prettyPrint = false).table {
            tr {
                th { +"Plain" }
            }
        }

        assertEquals("<table><tr><th>Plain</th></tr></table>", html)
    }
}
