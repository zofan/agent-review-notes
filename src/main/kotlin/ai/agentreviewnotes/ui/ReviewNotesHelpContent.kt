package ai.agentreviewnotes.ui

internal object ReviewNotesHelpContent {
    val text = """
        Create a code note: select code or place the caret, then press Ctrl+Alt+R or use the editor context menu.
        Create a directory note: use Add Review Note to Directory in the Project view context menu.

        Open note details by double-clicking a note or pressing Enter. Select a note and press F4 to open its target. Click a gutter marker to reveal its note in the Agent Review tool window. The selected code range is highlighted in the editor, and files and directories with active notes have an orange badge in Project view.

        Use the type and date range controls to filter the list; right-click a note or press Shift+F10 or the Menu key to edit, delete, resolve, or reopen it.

        Change the shortcut in Settings | Keymap | Agent Review Notes.

        Notes are local JSON files under .idea/agent-review-notes/notes. They are not sent over the network and do not modify source files.
    """.trimIndent()
}
