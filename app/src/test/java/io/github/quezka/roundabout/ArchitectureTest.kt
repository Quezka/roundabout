package io.github.quezka.roundabout

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// clean architecture rules ; check imports per layer
class ArchitectureTest {
    private val root = File("src/main/java/io/github/quezka/roundabout")
    private val base = "io.github.quezka.roundabout"

    private fun imports(layer: String): List<Pair<String, String>> =
        File(root, layer).walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().filter { it.startsWith("import ") }.map { f.name to it.removePrefix("import ").trim() }
        }.toList()

    private fun forbid(layer: String, vararg banned: String) {
        val bad = imports(layer).filter { (_, i) -> banned.any { i.startsWith(it) } }
        assertTrue("$layer breaks layering: $bad", bad.isEmpty())
    }

    @Test fun layersExist() {
        listOf("domain", "data", "di", "service", "ui").forEach { assertTrue(it, File(root, it).isDirectory) }
    }

    @Test fun domainIsPure() = forbid("domain", "android.", "androidx.", "$base.data", "$base.ui", "$base.di", "$base.service")

    @Test fun dataKnowsOnlyDomain() = forbid("data", "$base.ui", "$base.di", "$base.service")

    @Test fun serviceHasNoUi() = forbid("service", "$base.ui", "$base.di", "$base.data")

    @Test fun uiNeverTouchesData() = forbid("ui", "$base.data")
}
