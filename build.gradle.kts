import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "ai.agentreviewnotes"
version = "0.1.16"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation(kotlin("test"))

    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledModule("com.intellij.modules.vcs")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "ai.agentreviewnotes"
        name = "Agent Review Notes"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "261"
        }

        vendor {
            name = "0x9171"
        }
    }
}

tasks {
    processResources {
        from("skills/agent-review-notes") {
            include("SKILL.md", "scripts/**")
            into("agent-review-notes/skills/agent-review-notes")
        }
    }

    test {
        useJUnitPlatform()
    }

    matching { it.name == "instrumentCode" || it.name == "instrumentTestCode" }.configureEach {
        enabled = false
    }
}
