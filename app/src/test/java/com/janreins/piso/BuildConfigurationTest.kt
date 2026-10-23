package com.janreins.piso

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BuildConfigurationTest {
    @Test
    fun unusedCodeGenerationAndScreenshotToolingAreRemoved() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val app = File(root, "app/build.gradle.kts").readText()
        val configuration = app + File(root, "build.gradle.kts").readText() +
            File(root, "gradle/libs.versions.toml").readText()
        assertFalse(configuration.contains("roborazzi", ignoreCase = true))
        assertFalse(configuration.contains("secrets", ignoreCase = true))
        assertFalse(app.contains("buildConfig = true"))
        assertFalse(File(root, ".env.example").exists())
        assertFalse(File(root, "app/src/main").walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java") }
            .any { it.readText().contains("BuildConfig") })
    }
}
