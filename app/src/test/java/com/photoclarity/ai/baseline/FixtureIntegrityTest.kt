package com.photoclarity.ai.baseline

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class FixtureIntegrityTest {
    private fun bytes(name: String) = checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).use { it.readBytes() }
    @Test fun manifestChecksAllCommittedFixtures() {
        val lines = bytes("SHA256SUMS").toString(Charsets.UTF_8).trim().lines()
        assertEquals(3, lines.size)
        for (line in lines) {
            val (expected, name) = line.split("  ")
            val actual = bytes(name)
            assertTrue(actual.size > 10 * 1024)
            assertArrayEquals(byteArrayOf(-119,80,78,71,13,10,26,10), actual.take(8).toByteArray())
            assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(actual).joinToString("") { "%02x".format(it) })
        }
    }
    @Test fun duplicateIsByteIdenticalAndBrightnessVariantIsDifferent() {
        assertArrayEquals(bytes("base.png"), bytes("exact-copy.png"))
        assertFalse(bytes("base.png").contentEquals(bytes("brighter.png")))
    }
}
