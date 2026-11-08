package org.crux.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLibrariesTest {
    private val sample =
        """
        {
          "libraries": [
            {"uniqueId": "org.b:zeta", "name": "Zeta", "artifactVersion": "2.0", "licenses": ["MIT"], "website": "https://zeta.example"},
            {"uniqueId": "org.a:alpha", "name": "alpha", "artifactVersion": "1.0", "licenses": ["Apache-2.0", "X-Unknown"],
             "scm": {"url": "https://github.com/a/alpha.git"}},
            {"uniqueId": "com.github.x:bare", "name": "com.github.x:bare", "licenses": [], "website": "http://insecure.example"},
            {"name": "no id"},
            {"uniqueId": "org.b:zeta", "name": "Zeta duplicate", "licenses": []}
          ],
          "licenses": {
            "MIT": {"name": "MIT License"},
            "Apache-2.0": {"name": "Apache License 2.0"}
          }
        }
        """.trimIndent()

    @Test
    fun sortsByNameAndResolvesLicenseNames() {
        val libraries = parseOpenSourceLibraries(sample)
        assertEquals(listOf("alpha", "bare", "Zeta"), libraries.map { it.name })
        assertEquals(listOf("Apache License 2.0", "X-Unknown"), libraries.first().licenses)
        assertEquals("MIT License", libraries.last().licenses.single())
    }

    @Test
    fun usesScmUrlAndRejectsNonHttps() {
        val libraries = parseOpenSourceLibraries(sample).associateBy { it.id }
        assertEquals("https://github.com/a/alpha.git", libraries.getValue("org.a:alpha").website)
        assertNull(libraries.getValue("com.github.x:bare").website)
        assertTrue(libraries.getValue("com.github.x:bare").licenses.isEmpty())
    }

    @Test
    fun skipsEntriesWithoutIdAndDuplicates() {
        assertEquals(3, parseOpenSourceLibraries(sample).size)
    }

    @Test
    fun malformedInputGivesEmptyList() {
        assertTrue(parseOpenSourceLibraries("not json").isEmpty())
        assertTrue(parseOpenSourceLibraries("{}").isEmpty())
    }

    @Test
    fun swiperGetsItsCheckedLicenseWhenThePomDeclaresNone() {
        val json = """{"libraries":[{"uniqueId":"com.github.tlaster:swiper","name":"swiper","licenses":[]}]}"""
        assertEquals(listOf("MIT License"), parseOpenSourceLibraries(json).single().licenses)
    }
}
