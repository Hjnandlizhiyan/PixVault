package com.pixvault.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticQueryTranslatorTest {
    @Test
    fun translatesCommonChinesePhotoDescription() {
        val result = SemanticQueryTranslator.normalize("海边的日落")

        assertTrue(result.translated)
        assertEquals("sunset at the beach", result.encoderText)
    }

    @Test
    fun combinesMultipleChineseConcepts() {
        val result = SemanticQueryTranslator.normalize("雨天窗边的猫")

        assertTrue(result.translated)
        assertTrue(result.encoderText.contains("cat"))
        assertTrue(result.encoderText.contains("rainy"))
    }

    @Test
    fun leavesEnglishQueryUnchanged() {
        val result = SemanticQueryTranslator.normalize("purple sky")

        assertFalse(result.translated)
        assertEquals("purple sky", result.encoderText)
    }
}
