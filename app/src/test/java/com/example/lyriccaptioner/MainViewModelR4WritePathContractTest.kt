package com.example.lyriccaptioner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for the three production A+/A- write paths.  A local JVM test
 * cannot construct Android's Context without adding a Robolectric dependency,
 * so this keeps the first-wave proof focused on the source-level invariant:
 * each path must call the canonical ratio helper rather than writing only the
 * legacy fontSizeSp projection.
 */
class MainViewModelR4WritePathContractTest {
    private val source by lazy {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        var directory = workingDirectory.canonicalFile
        val sourceFile = generateSequence(directory) { it.parentFile }
            .map { File(it, "app/src/main/java/com/example/lyriccaptioner/MainViewModel.kt") }
            .firstOrNull(File::isFile)
            ?: error("MainViewModel.kt not found from ${workingDirectory.canonicalPath}")
        sourceFile.readText()
    }

    @Test
    fun defaultFontSizeWriteUsesCanonicalRatioHelper() {
        assertTrue(methodBody("updateFontSize").contains("withFontSizeRatio"))
    }

    @Test
    fun selectedCueFontSizeWriteUsesCanonicalRatioHelper() {
        assertTrue(methodBody("updateSelectedCueFontSize").contains("withFontSizeRatio"))
    }

    @Test
    fun cueIdFontSizeWriteUsesCanonicalRatioHelper() {
        assertTrue(methodBody("updateCueFontSize").contains("withFontSizeRatio"))
    }

    private fun methodBody(name: String): String {
        val start = source.indexOf("fun $name")
        check(start >= 0) { "missing production method: $name" }
        val next = source.indexOf("\n    fun ", start + 1).takeIf { it >= 0 } ?: source.length
        return source.substring(start, next)
    }
}
