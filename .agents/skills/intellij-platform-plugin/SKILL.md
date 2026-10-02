---
name: intellij-platform-plugin
description: Build/retarget JetBrains IntelliJ Platform plugins (Gradle).
tags: [intellij, jetbrains, gradle, kotlin, plugin, ide]
---

# IntelliJ Platform Plugin Development

Use when building, retargeting (e.g. GoLand -> all IDEs), or debugging a JetBrains
IntelliJ Platform plugin that uses the `org.jetbrains.intellij.platform` Gradle plugin
(2.x) and Kotlin.

## Key concepts

- **Platform vs product**: the IntelliJ *Platform* (shared by all JetBrains IDEs) has a
  build number like `261` (= 2026.1) or `262` (= 2026.2). Each IDE product (GoLand,
  IDEA, PyCharm) sits on top of a platform build. A plugin that only depends on
  `com.intellij.modules.platform` (+ bundled plugins like `Git4Idea`) works in EVERY
  JetBrains IDE of a compatible platform build.
- **`intellijIdea()` is the base platform.** Target it for "works in all JetBrains IDEs".
  The old `create("IC", ...)` (IntelliJ IDEA Community) is DEPRECATED since 2025.3 (build
  253) and fails with: `IntelliJ IDEA Community (IC) is no longer published ... use:
  intellijIdea("...")`. `create("GO", ...)` / `create("IU", ...)` target a single product.

## Gradle DSL (2.x) gotchas

```kotlin
dependencies {
    intellijPlatform {
        intellijIdea("2026.1.1")   // base platform (all IDEs)
        bundledModule("com.intellij.modules.vcs")
        bundledPlugin("Git4Idea")
    }
}
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"     // lowest supported platform major
            // omit until-build for forward compatibility
        }
    }
}
```

- `bundledPlugin("X")` pulls a *bundled plugin*; `bundledModule("X")` pulls a *platform
  module*. They are DIFFERENT. Declaring `<depends>X</depends>` in plugin.xml alone does
  NOT add the module to the compile classpath — you must also add it via
  `bundledModule(...)` in the Gradle DSL.
- git4idea classes moved: `GitRepositoryManager` now extends
  `com.intellij.dvcs.repo.AbstractRepositoryManager`, and `GitRepository` extends
  `com.intellij.dvcs.repo.Repository`. Those supertypes live in the VCS platform module
  (`intellij.platform.vcs.dvcs` / `.dvcs.impl`), NOT inside the git4idea plugin jar. If you
  use `getRepositoryForFileQuick()`, `.repositories`, `repository.root`, etc. you must add
  `com.intellij.modules.vcs` + `intellij.platform.vcs.dvcs` + `intellij.platform.vcs.dvcs.impl`
  as bundled modules, plus `<depends>com.intellij.modules.vcs</depends>` in plugin.xml.
  Symptom without them: `Cannot access 'AbstractRepositoryManager' which is a supertype of
  'GitRepositoryManager'` and `Unresolved reference 'getRepositoryForFileQuick'`.

## Java / JVM target must match the platform

Each platform major has a required Java version (both Kotlin `jvmTarget` and the Java
toolchain must match the LOWEST platform you support):

| platform build | version | requires Java |
|---|---|---|
| 261 | 2026.1 | 21 |
| 262 | 2026.2 | 25 |

- Set `kotlin { compilerOptions { jvmTarget = ... } }` AND
  `java { toolchain { languageVersion = ... } }` to the same value. Mismatch between the two
  fails with `Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks`.
- To support a version RANGE (e.g. 2026.1 AND 2026.2), target the LOWEST Java (21) and
  build against the lowest platform (261): Java-21 bytecode runs on Java-25 runtimes too.
- `verifyPluginProjectConfiguration` flags every mismatch. Run it and make it pass clean.

## `since-build` / `until-build` rules

- `since-build` = lowest platform major you support. "Support 2026+" means `since-build=261`
  (2026.1), NOT 262.
- Build against the LOWEST supported version so the compiler guarantees you use only
  compatible APIs. Building against 262 while declaring `since-build=261` triggers a
  verifier warning (correctly: newer APIs could silently creep in).
- OMIT `until-build` for forward compatibility (2024.3+/243+). Setting it blocks installs on
  future IDE versions.

## Version bumps and release artifacts

For a request to “bump the plugin version,” inspect the repository’s current version and recent release commits first, then increment the intended SemVer component (default to patch when the user gives no target). Search for every occurrence of both the old and new version so duplicated metadata does not drift.

After editing:

1. Run the repository’s canonical plugin build (`make build` when provided, otherwise `./gradlew buildPlugin`).
2. Confirm the build generated `build/distributions/<plugin-id>-<new-version>.zip`; do not report an older ZIP merely because it is still present in the directory.
3. Keep version bump and commit as separate actions unless the user explicitly asked to commit. If they ask only to build, rebuild the current working tree and report the exact new-version artifact path.

## Diagnostics

- Inspect a class's real supertype/methods: `javap -cp <jars> <fqcn>` (use a JDK's `javap`;
  the JBR `bin/` ships `java`/`javac` but NOT `javap`).
- Find which jar holds a class inside the downloaded platform (Gradle cache):
  `python3 -c "import zipfile; z=zipfile.ZipFile(jar); print('HIT' if 'com/intellij/dvcs/repo/AbstractRepositoryManager.class' in z.namelist() else None)"`
  over `find ... -name '*.jar'`. The extracted platform lives under
  `~/.gradle/caches/9.5.0/transforms/<hash>/transformed/idea-<ver>/`.
- Print the resolved compile classpath: an init script
  `allprojects { tasks.register("printCp") { doLast { configurations.compileClasspath.files.forEach { println(it) } } } }`
  run via `./gradlew -I /tmp/printcp.gradle printCp -q`.
- Inspect platform layout/modules: `product-info.json` in the extracted platform dir maps
  module names -> jar classpath (look for `kind: productModuleV2` / `pluginAlias`).
- Verify the FINAL packaged compatibility metadata: the distribution ZIP nests twice —
  `build/distributions/<name>-<ver>.zip` → `<name>/lib/<name>-<ver>.jar` → `META-INF/plugin.xml`.
  Extract both layers and read `<idea-version since-build=... until-build=...>` and
  `<depends>` to confirm the shipped artifact actually declares the intended range/deps (the
  `patchPluginXml` task rewrites this file, so the source `plugin.xml` is not the shipped truth).
  One-liner: read the inner jar bytes with `zipfile` from the outer zip, dump to a temp file,
  then read `META-INF/plugin.xml` from it.

## Threading: read-action / write-action requirements

Background work in a plugin (e.g. `CompletableFuture.supplyAsync(..., AppExecutorUtil.getAppExecutorService())`)
frequently trips the read-access assertion: `Read access is allowed from inside read-action only`
(`ThreadingAssertions.softAssertReadAccess`). Know which APIs need a read action vs. which are thread-safe:

- **NEEDS read action** (wrap in `ApplicationManager.getApplication().runReadAction { ... }`):
  - `FileDocumentManager.getInstance().getDocument(file)` — the classic culprit. Calling it from a
    background thread throws the soft read-access assertion.
  - Reading `Document` content (e.g. `document.immutableCharSequence.toString()`, any
    `document.getText()` / `getCharsSequence()`).
- **Thread-safe, NO read action needed** (safe on a background thread):
  - `LocalFileSystem.getInstance().findFileByNioFile(path)` — VFS lookups are fine off-EDT.
  - `GitRepositoryManager.getInstance(project).repositories` and repository metadata.
- **Rule of thumb**: resolve VFS paths / repo roots on the background thread, then do a SINGLE
  `runReadAction` that performs all `FileDocumentManager` + `Document` access and any model reads,
  and return a plain-data outcome. Touch the editor / `OpenFileDescriptor.navigate()` / `ProjectView`
  back on the EDT via `invokeLater`.
- In Kotlin, returning early from inside a `runReadAction(Computable { ... })` lambda uses
  `return@compute` (the `Computable` label), NOT `return`.

A common project pattern is a small helper object:

```kotlin
internal object ReviewNoteReadAction {
    fun <T> compute(action: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable(action))
}
```

## Pitfalls

- The JetBrains Toolbox JBR `bin/` does not include `javap`; use a JDK from
  `~/.gradle/jdks/*/bin/javap` or install one.
- `make` wrappers that resolve `$(HOME)` break inside Hermes sessions (HOME is overridden to
  the profile dir). Run `HOME=/home/admin make build` or invoke `./gradlew` directly with an
  explicit `JAVA_HOME`.
- The Kotlin plugin's `JvmTarget` enum supports up to `JVM_25` in Kotlin 2.3.0 — but only
  pick a target the platform actually requires (see table above).
- Some plugins ship **source-structure contract tests**: a JUnit test that reads the plugin's
  own `.kt` source file with `Files.readString(...)` and asserts ordering/absence of code via
  string matching (e.g. `assertTrue(src.indexOf("getDocument(file)") < src.indexOf("..."))`).
  These encode *assumptions about code layout*, and can themselves encode a bug (a test that
  asserts `getDocument` sits OUTSIDE a read action). When you refactor — e.g. move a call
  inside a `runReadAction` — the contract test breaks and MUST be updated in the same change,
  with the assertion corrected to the new (fixed) contract, not deleted. Grep for
  `Files.readString` / `substringBefore("private fun` in `src/test` before touching code that
  such a test might pin down.
- After retargeting a plugin away from a specific product (e.g. GoLand -> base platform),
  sweep the build wrapper and docs for stale product references that now mislead: the
  Makefile's default JBR path (`.../apps/goland/jbr` -> `.../apps/intellij-idea/jbr`),
  its variable name (`GOLAND_JBR` -> `IDE_JBR`), and README wording ("GoLand plugin" ->
  "JetBrains IDE plugin"). The JBR itself is just a Java runtime for Gradle, so this is
  cosmetic — but leaving "goland" in the default path after a cross-IDE retarget confuses
  the next person.
