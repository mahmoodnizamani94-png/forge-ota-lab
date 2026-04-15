package dev.forgeotalab.data.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ForgeTypeConverters].
 *
 * WHY test round-trips: Type converters are the most fragile part of Room —
 * a broken converter silently corrupts data or crashes on read. These tests
 * catch regressions before they reach the database.
 */
class ForgeTypeConvertersTest {

    private val converters = ForgeTypeConverters()

    // --- String list round-trip tests ---

    @Test
    fun `string list round-trips through JSON correctly`() {
        val original = listOf("partition_a", "partition_b", "partition_c")
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun `empty string list round-trips correctly`() {
        val original = emptyList<String>()
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun `null string list converts to null`() {
        assertNull(converters.fromStringList(null))
        assertNull(converters.toStringList(null))
    }

    @Test
    fun `single-element string list round-trips correctly`() {
        val original = listOf("boot")
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun `string list with special characters round-trips correctly`() {
        val original = listOf("system_a", "vendor/boot", "dtbo-verified")
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun `malformed JSON returns empty list instead of crashing`() {
        // WHY: Forward-compatibility — if a future version writes a different
        // format, older versions should degrade gracefully, not crash.
        val result = converters.toStringList("not valid json")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `string list JSON output is valid JSON array`() {
        val original = listOf("a", "b", "c")
        val json = converters.fromStringList(original)!!
        assert(json.startsWith("[")) { "Expected JSON array, got: $json" }
        assert(json.endsWith("]")) { "Expected JSON array, got: $json" }
    }
}
