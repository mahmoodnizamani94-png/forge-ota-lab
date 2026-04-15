package dev.forgeotalab.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DataResult] sealed class and extension functions.
 */
class DataResultTest {

    @Test
    fun `Success holds data correctly`() {
        val result = DataResult.Success(42)
        assertTrue(result is DataResult.Success)
        assertEquals(42, result.data)
    }

    @Test
    fun `Error holds message and cause`() {
        val cause = RuntimeException("test")
        val result = DataResult.Error("something failed", cause)
        assertTrue(result is DataResult.Error)
        assertEquals("something failed", result.message)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `Error without cause defaults to null`() {
        val result = DataResult.Error("something failed")
        assertNull(result.cause)
    }

    @Test
    fun `map transforms success data`() {
        val result: DataResult<Int> = DataResult.Success(21)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is DataResult.Success)
        assertEquals(42, (mapped as DataResult.Success).data)
    }

    @Test
    fun `map preserves error unchanged`() {
        val result: DataResult<Int> = DataResult.Error("fail")
        val mapped = result.map { it * 2 }
        assertTrue(mapped is DataResult.Error)
        assertEquals("fail", (mapped as DataResult.Error).message)
    }

    @Test
    fun `flatMap chains successful operations`() {
        val result: DataResult<Int> = DataResult.Success(21)
        val chained = result.flatMap { DataResult.Success(it * 2) }
        assertTrue(chained is DataResult.Success)
        assertEquals(42, (chained as DataResult.Success).data)
    }

    @Test
    fun `flatMap short-circuits on error`() {
        val result: DataResult<Int> = DataResult.Error("fail")
        val chained = result.flatMap { DataResult.Success(it * 2) }
        assertTrue(chained is DataResult.Error)
        assertEquals("fail", (chained as DataResult.Error).message)
    }

    @Test
    fun `flatMap propagates error from inner operation`() {
        val result: DataResult<Int> = DataResult.Success(21)
        val chained = result.flatMap { DataResult.Error("inner fail") }
        assertTrue(chained is DataResult.Error)
        assertEquals("inner fail", (chained as DataResult.Error).message)
    }

    @Test
    fun `getOrNull returns data on success`() {
        val result: DataResult<String> = DataResult.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `getOrNull returns null on error`() {
        val result: DataResult<String> = DataResult.Error("fail")
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrThrow returns data on success`() {
        val result: DataResult<String> = DataResult.Success("hello")
        assertEquals("hello", result.getOrThrow())
    }

    @Test(expected = RuntimeException::class)
    fun `getOrThrow throws on error without cause`() {
        val result: DataResult<String> = DataResult.Error("fail")
        result.getOrThrow()
    }

    @Test(expected = IllegalStateException::class)
    fun `getOrThrow throws original cause when present`() {
        val cause = IllegalStateException("original")
        val result: DataResult<String> = DataResult.Error("fail", cause)
        result.getOrThrow()
    }

    @Test
    fun `when expression is exhaustive on DataResult`() {
        // WHY: Compile-time check — if a new subclass is added to DataResult,
        // this when expression would fail to compile without a new branch.
        val result: DataResult<Int> = DataResult.Success(1)
        val message = when (result) {
            is DataResult.Success -> "success: ${result.data}"
            is DataResult.Error -> "error: ${result.message}"
        }
        assertEquals("success: 1", message)
    }
}
