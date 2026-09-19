package com.pixvault.data.util

import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

object Hashing {
    fun copyAndSha256(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(output, digest).use { digestOutput ->
            input.copyTo(digestOutput)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
