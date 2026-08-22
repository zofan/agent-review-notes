package ai.agentreviewnotes.ui

internal object ReviewNotesHelpContent {
    val text = """
        Create a code note: select code or place the caret, then press Ctrl+Alt+R or use the editor context menu.
        Create a directory note: use Add Review Note to Directory in the Project view context menu.

        Open note details by double-clicking a note or pressing Enter. Select a note and press F4 to open its target. Click the compact note text shown above its anchor to open details. Use the one Edit button to change Type, Status, and Note together, regardless of its current status; Save or Cancel without opening another dialog. Delete remains a confirmed destructive action. Press Ctrl+Alt+Shift+R while the caret is on a noted line or a selection intersects a note; overlapping notes are offered in a chooser. The selected code range is highlighted in the editor with type-specific colors, and files and directories with visible notes receive badges in Project view.

        Use the type and date range controls to filter the list; right-click a note or press Shift+F10 or the Menu key to edit, delete, resolve, or reopen it. Use Feature for requested new behavior or a substantial capability extension; use Suggestion for a local optional improvement.

        Change the shortcut in Settings | Keymap | Agent Review Notes.

        Notes are local JSON files under .idea/agent-review-notes/notes. They are not sent over the network and do not modify source files.
    """.trimIndent()
}
