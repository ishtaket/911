package com.searchaid.data.local.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `fromStringList with empty list returns empty string`() {
        assertEquals("", converters.fromStringList(emptyList()))
    }

    @Test
    fun `toStringList with empty string returns empty list`() {
        assertEquals(emptyList<String>(), converters.toStringList(""))
    }

    @Test
    fun `roundtrip single item`() {
        val input = listOf("alice")
        val serialized = converters.fromStringList(input)
        assertEquals(input, converters.toStringList(serialized))
    }

    @Test
    fun `roundtrip multiple items`() {
        val input = listOf("alice", "bob", "charlie")
        val serialized = converters.fromStringList(input)
        assertEquals(input, converters.toStringList(serialized))
    }

    @Test
    fun `items with commas are preserved`() {
        val input = listOf("last, first", "another, name")
        val serialized = converters.fromStringList(input)
        assertEquals(input, converters.toStringList(serialized))
    }

    @Test
    fun `separator is triple pipe`() {
        val serialized = converters.fromStringList(listOf("a", "b", "c"))
        assertEquals("a|||b|||c", serialized)
    }
}
