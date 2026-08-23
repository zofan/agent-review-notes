# Agent Review Notes

An experimental JetBrains IDE plugin for local code review comments. The user selects code or places the
caret, writes only the comment text, and the plugin saves the path, range, Git snapshot, and context
into an open JSON contract for an AI agent.

## Features

The plugin supports a complete local workflow:

1. create a comment from editor selection/caret;
2. save it without modifying the source file;
3. show a compact `type · status · branch · file:line — text` row in the tool window, filter
   comments by branch and owning repository, and mark the type with its own icon;
4. show the compact comment text above the anchor without modifying the source, open details by
   clicking the inlay, and with a single `Edit` button switch Details into atomic editing mode for
   type, status, and text regardless of the current status, or delete the comment;
5. open the comment under the caret or intersecting selection via `Ctrl+Alt+Shift+R`, offering a
   chooser when there are intersections;
6. survive external changes to the JSON and the code;
7. either rebind the comment precisely or mark it as requiring manual binding;
8. assign normalized tags and dependencies on other comments;
9. select a thematic queue by tags and build a safe execution order via the built-in skill.

Workspace paths are stored exactly as they appear in the project. Comments can therefore be created
and opened in registered Git repositories mounted into a multirepo through symlinks, e.g.
`golang/handler`, while the Git metadata belongs to the owning repository behind the symlink.
Arbitrary symlinks pointing outside and nested escapes beyond the canonical root owning repository
are rejected.

## Local data

Project comments are stored outside Git in:

```text
<project>/.idea/agent-review-notes/notes/<id>.json
```

One comment corresponds to one JSON file. The plugin reads the `agent.review.note.v1`,
`agent.review.note.v2`, and `agent.review.note.v3` contracts: v2 adds the `feature` type, and v3 adds
the `tags` and `dependsOn` arrays for thematic selection and DAG execution order. Existing v1/v2
comments remain compatible without migration. The plugin does not use the network and does not run AI
agents.

## Build

Gradle can be run on a bundled JetBrains JBR. Java 21 is required as the compiler toolchain; the
Foojay resolver downloads it automatically if there is no local JDK 21 yet:

```bash
make check
make build
```

The plugin ZIP appears in `build/distributions/`.

Main commands:

```bash
make help           # list of commands
make test           # Kotlin and Python tests
make build          # ZIP without running tests
make verify         # Plugin Verifier
make release        # clean build with all release gates
make artifacts      # SHA-256 of the built ZIPs
make run            # sandbox IDE
```

By default the Makefile uses the JBR from JetBrains Toolbox:
`$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/jbr`. A different JDK can be passed via
`JAVA_HOME` or `IDE_JBR`.

## Status

A ready local solution for JetBrains IDEs. Manual verification of installation and the main scenarios
in a supported IDE version is recommended before public distribution.

## TODO
