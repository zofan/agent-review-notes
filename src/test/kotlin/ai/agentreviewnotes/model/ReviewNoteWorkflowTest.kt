package ai.agentreviewnotes.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewNoteWorkflowTest {
    @Test
    fun `tags normalize to unique lowercase stable values`() {
        assertEquals(
            listOf("component:sage", "flow:mcp"),
            ReviewNoteWorkflow.parseTags(" Flow:MCP, component:sage, flow:mcp "),
        )
    }

    @Test
    fun `invalid tag is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteWorkflow.parseTags("sage,not allowed")
        }
    }
}