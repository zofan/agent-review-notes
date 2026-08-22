package ai.agentreviewnotes.skill

internal object BundledReviewSkill {
    private const val ROOT = "/agent-review-notes/skills/agent-review-notes"

    fun content(): String = resource("SKILL.md")

    fun files(): Map<String, String> = linkedMapOf(
        "SKILL.md" to content(),
        "scripts/review_notes.py" to resource("scripts/review_notes.py"),
    )

    private fun resource(relativePath: String): String =
        requireNotNull(BundledReviewSkill::class.java.getResourceAsStream("$ROOT/$relativePath")) {
            "Bundled Agent Review Notes skill file is missing: $relativePath"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
