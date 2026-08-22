package ai.agentreviewnotes.skill

internal object BundledReviewSkill {
    private const val RESOURCE = "/agent-review-notes/skills/agent-review-notes/SKILL.md"

    fun content(): String = requireNotNull(BundledReviewSkill::class.java.getResourceAsStream(RESOURCE)) {
        "Bundled Agent Review Notes skill is missing"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
