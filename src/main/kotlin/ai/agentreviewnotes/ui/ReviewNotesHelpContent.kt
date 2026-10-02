package ai.agentreviewnotes.ui

internal enum class ReviewNotesHelpLanguage(val title: String) {
    ENGLISH("English"),
    RUSSIAN("Русский"),
    CHINESE("中文"),
    SPANISH("Español"),
    GERMAN("Deutsch"),
    JAPANESE("日本語");

    override fun toString(): String = title
}

internal object ReviewNotesHelpContent {
    fun text(language: ReviewNotesHelpLanguage): String = when (language) {
        ReviewNotesHelpLanguage.ENGLISH -> """
            Create a general task with Add note in the toolbar. Create a code note by selecting code or placing the caret, then press Ctrl+Alt+R or use the editor context menu. To comment on a whole file or directory, right-click it in Project view and choose Add Review Note.

            Open details by double-clicking a note or pressing Enter. Press F4 to open its target. The compact note text shown above its anchor also opens details. The one Edit button changes Type, Status, and Note together, plus Tags and Depends on, regardless of its current status; use Save or Cancel. Dependencies must not form a cycle. Delete asks for confirmation and is unavailable while another note depends on this note. Ctrl+Alt+Shift+R opens notes at the caret. Anchors are highlighted in the editor; files and directories with visible notes have Project view badges.

            Use the type, date range, status, branch, and repository controls to filter the list. Right-click a note, press Shift+F10, or use the Menu key to edit, delete, resolve, or reopen it. Feature means requested new behavior; Suggestion means an optional local improvement.

            Change shortcuts in Settings | Keymap | Agent Review Notes. Notes stay in .idea/agent-review-notes/notes, are never sent over the network, and do not modify source files. Install SKILL lets an AI agent discover and process them.
        """.trimIndent()
        ReviewNotesHelpLanguage.RUSSIAN -> """
            Кнопка Add note на панели создаёт общую задачу без привязки к файлу. Чтобы создать заметку к коду, выделите фрагмент или поставьте курсор и нажмите Ctrl+Alt+R либо выберите команду в контекстном меню редактора. Для заметки ко всему файлу или каталогу щёлкните по нему правой кнопкой в окне Project и выберите Add Review Note.

            Откройте подробности двойным щелчком или клавишей Enter. F4 открывает цель заметки. Короткий текст над отмеченным фрагментом также открывает подробности. Кнопка Edit позволяет вместе изменить тип, статус, текст, теги и зависимости; затем нажмите Save или Cancel. Зависимости не должны образовывать цикл. Удаление требует подтверждения и недоступно, пока от заметки зависит другая. Ctrl+Alt+Shift+R открывает заметки у курсора. Фрагменты подсвечиваются в редакторе, а файлы и каталоги с заметками отмечаются значками в Project.

            Фильтруйте список по типу, дате, статусу, ветке и репозиторию. Контекстное меню, Shift+F10 или клавиша Menu позволяют изменить, удалить, завершить или переоткрыть заметку. Feature — новое поведение, Suggestion — необязательное локальное улучшение.

            Горячие клавиши настраиваются в Settings | Keymap | Agent Review Notes. Заметки хранятся локально в .idea/agent-review-notes/notes, не отправляются в сеть и не меняют исходные файлы. Install SKILL помогает ИИ-агенту находить и обрабатывать их.
        """.trimIndent()
        ReviewNotesHelpLanguage.CHINESE -> """
            工具栏中的 Add note 可创建不关联文件的通用任务。要为代码添加备注，请选择代码或放置光标，然后按 Ctrl+Alt+R，或使用编辑器右键菜单。要为整个文件或目录添加备注，请在 Project 视图中右键单击它并选择 Add Review Note。

            双击备注或按 Enter 查看详情；按 F4 打开目标。锚点上方的简短文字也可打开详情。使用 Edit 可一次修改类型、状态、正文、标签和依赖项，然后选择 Save 或 Cancel。依赖关系不能形成循环。删除需要确认；如果其他备注依赖当前备注，则不能删除。Ctrl+Alt+Shift+R 可打开光标处的备注。代码锚点会高亮，带备注的文件和目录会在 Project 视图中显示标记。

            可按类型、日期、状态、分支和仓库筛选。右键菜单、Shift+F10 或 Menu 键可编辑、删除、解决或重新打开备注。Feature 表示新功能，Suggestion 表示可选的局部改进。

            快捷键可在 Settings | Keymap | Agent Review Notes 中修改。备注仅保存在 .idea/agent-review-notes/notes，不会发送到网络，也不会修改源文件。Install SKILL 可帮助 AI 代理发现并处理备注。
        """.trimIndent()
        ReviewNotesHelpLanguage.SPANISH -> """
            Add note en la barra crea una tarea general sin archivo asociado. Para crear una nota de código, selecciona código o coloca el cursor y pulsa Ctrl+Alt+R, o usa el menú contextual del editor. Para comentar un archivo o directorio completo, haz clic derecho en Project y elige Add Review Note.

            Abre los detalles con doble clic o Enter. F4 abre el destino. El texto breve sobre el anclaje también abre los detalles. Edit permite cambiar a la vez tipo, estado, texto, etiquetas y dependencias; después usa Save o Cancel. Las dependencias no pueden formar ciclos. El borrado pide confirmación y no está disponible si otra nota depende de esta. Ctrl+Alt+Shift+R abre las notas del cursor. Los anclajes se resaltan y los archivos y directorios con notas muestran una insignia en Project.

            Filtra por tipo, fecha, estado, rama y repositorio. El menú contextual, Shift+F10 o la tecla Menu permiten editar, borrar, resolver o reabrir. Feature indica una función nueva; Suggestion, una mejora local opcional.

            Cambia los atajos en Settings | Keymap | Agent Review Notes. Las notas permanecen en .idea/agent-review-notes/notes, no se envían por la red ni modifican el código. Install SKILL ayuda al agente de IA a encontrarlas y procesarlas.
        """.trimIndent()
        ReviewNotesHelpLanguage.GERMAN -> """
            Add note in der Werkzeugleiste erstellt eine allgemeine Aufgabe ohne Dateibezug. Um eine Code-Notiz zu erstellen, markiere Code oder setze den Cursor und drücke Ctrl+Alt+R; alternativ nutze das Kontextmenü des Editors. Für eine Notiz zu einer ganzen Datei oder einem Ordner klicke im Project-Fenster mit der rechten Maustaste darauf und wähle Add Review Note.

            Öffne Details per Doppelklick oder Enter. F4 öffnet das Ziel. Auch der kurze Text über der Markierung öffnet die Details. Mit Edit lassen sich Typ, Status, Text, Tags und Abhängigkeiten gemeinsam ändern; danach Save oder Cancel wählen. Abhängigkeiten dürfen keinen Zyklus bilden. Löschen erfordert eine Bestätigung und ist gesperrt, wenn eine andere Notiz hiervon abhängt. Ctrl+Alt+Shift+R öffnet Notizen am Cursor. Markierungen werden im Editor hervorgehoben; Dateien und Ordner mit Notizen erhalten im Project-Fenster ein Symbol.

            Filtere nach Typ, Zeitraum, Status, Branch und Repository. Kontextmenü, Shift+F10 oder die Menu-Taste ermöglichen Bearbeiten, Löschen, Abschließen und Wiederöffnen. Feature steht für neues Verhalten, Suggestion für eine optionale lokale Verbesserung.

            Tastenkürzel lassen sich unter Settings | Keymap | Agent Review Notes ändern. Notizen bleiben in .idea/agent-review-notes/notes, werden nicht übertragen und verändern keine Quelldateien. Install SKILL hilft dem KI-Agenten, sie zu finden und zu bearbeiten.
        """.trimIndent()
        ReviewNotesHelpLanguage.JAPANESE -> """
            ツールバーの Add note では、ファイルに関連付けない一般タスクを作成できます。コードにメモを作成するには、コードを選択するかカーソルを置き、Ctrl+Alt+R を押すかエディターのコンテキストメニューを使います。ファイルまたはディレクトリ全体へのメモは、Project ビューで右クリックして Add Review Note を選びます。

            詳細はダブルクリックまたは Enter で開き、F4 で対象を開きます。アンカー上の短いメモ文からも詳細を開けます。Edit では種類、状態、本文、タグ、依存関係をまとめて変更し、Save または Cancel を選びます。依存関係は循環できません。他のメモが依存している場合は削除できず、削除時には確認が表示されます。Ctrl+Alt+Shift+R でカーソル位置のメモを開けます。アンカーはエディターで強調され、メモのあるファイルやディレクトリには Project ビューでバッジが付きます。

            種類、期間、状態、ブランチ、リポジトリで絞り込めます。右クリック、Shift+F10、Menu キーから編集、削除、解決、再オープンができます。Feature は新しい機能、Suggestion は任意の小さな改善です。

            ショートカットは Settings | Keymap | Agent Review Notes で変更できます。メモは .idea/agent-review-notes/notes にのみ保存され、ネットワーク送信やソース変更は行いません。Install SKILL により AI エージェントがメモを発見して処理できます。
        """.trimIndent()
    }
}
