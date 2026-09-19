package com.pixvault.data.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HashingTest {
    @Test
    fun copyAndSha256CopiesBytesAndReturnsStableDigest() {
        val bytes = "PixVault".toByteArray()
        val output = ByteArrayOutputStream()

        val digest = Hashing.copyAndSha256(ByteArrayInputStream(bytes), output)

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals("4efd68b5244c53907ff1e3aeb52066e9aff200f27a131b38896cb436260ed8f6", digest)
    }
}
