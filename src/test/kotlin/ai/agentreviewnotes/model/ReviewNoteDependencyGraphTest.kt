package ai.agentreviewnotes.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReviewNoteDependencyGraphTest {
    @Test
    fun `graph rejects missing dependency`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteDependencyGraph.validate(listOf(note(FIRST, listOf(SECOND))))
        }
    }

    @Test
    fun `graph rejects dependency cycle`() {
        assertFailsWith<IllegalArgumentException> {
            ReviewNoteDependencyGraph.validate(
                listOf(note(FIRST, listOf(SECOND)), note(SECOND, listOf(FIRST))),
            )
        }
    }

    private fun note(id: String, dependencies: List<String>) = ReviewNote(
        schema = REVIEW_NOTE_SCHEMA_V3,
        id = id,
        status = "open",
        kind = "bug",
        message = "test",
        location = NoteLocation("src/main.go", null, null, null, "0".repeat(64), 0, 1, 1, 1),
        anchor = NoteAnchor("x", "", "", null),
        createdAt = "2026-08-23T00:00:00Z",
        dependsOn = dependencies,
    )

    private companion object {
        const val FIRST = "123e4567-e89b-42d3-a456-426614174000"
        const val SECOND = "b23e4567-e89b-42d3-a456-426614174000"
    }
}