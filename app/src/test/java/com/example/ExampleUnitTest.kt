package com.example

import com.example.ai.BrstiPersonality
import com.example.data.local.BrstiSettings
import com.example.data.local.MemoryPreferenceEntity
import com.example.device.MathEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun mathEvaluator_handlesArithmeticAndFunctions() {
        val res1 = MathEvaluator.evaluate("(25 * 4) + sqrt(144)")
        assertEquals(112.0, res1.getOrThrow(), 1e-6)

        val res2 = MathEvaluator.evaluate("2 ^ 5 - 10")
        assertEquals(22.0, res2.getOrThrow(), 1e-6)
    }

    @Test
    fun personality_personalizesForOwnerAndRespectsMemoryConsent() {
        val withConsent = BrstiPersonality.buildSystemInstruction(
            settings = BrstiSettings(ownerName = "Swapnil", memoryConsentGranted = true),
            memories = listOf(MemoryPreferenceEntity(key = "tea", value = "Masala Chai"))
        )
        assertTrue(withConsent.contains("Swapnil"))
        assertTrue(withConsent.contains("Masala Chai"))

        val withoutConsent = BrstiPersonality.buildSystemInstruction(
            settings = BrstiSettings(ownerName = "Swapnil", memoryConsentGranted = false),
            memories = listOf(MemoryPreferenceEntity(key = "tea", value = "Masala Chai"))
        )
        assertTrue(!withoutConsent.contains("Masala Chai"))
    }
}
