package com.obscura.security

import androidx.annotation.Keep
import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.pow

/**
 * Cryptographically Secure Password & Secret Generator
 * Calculates entropy, strength score (0-100), and provides strength metrics.
 */
@Keep
object PasswordGenerator {

    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    data class GeneratorConfig(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeDigits: Boolean = true,
        val includeSymbols: Boolean = true
    )

    data class PasswordStrength(
        val score: Int, // 0 to 100
        val label: String, // Weak, Medium, Strong, Excellent
        val entropyBits: Double,
        val feedback: List<String>
    )

    /**
     * Generates a random secure password matching config options.
     */
    fun generatePassword(config: GeneratorConfig = GeneratorConfig()): String {
        val charPool = StringBuilder().apply {
            if (config.includeLowercase) append(LOWERCASE)
            if (config.includeUppercase) append(UPPERCASE)
            if (config.includeDigits) append(DIGITS)
            if (config.includeSymbols) append(SYMBOLS)
        }.toString()

        if (charPool.isEmpty()) return ""

        val random = SecureRandom()
        val password = StringBuilder()

        // Ensure at least one character from each enabled set
        val mandatory = mutableListOf<Char>()
        if (config.includeLowercase) mandatory.add(LOWERCASE[random.nextInt(LOWERCASE.length)])
        if (config.includeUppercase) mandatory.add(UPPERCASE[random.nextInt(UPPERCASE.length)])
        if (config.includeDigits) mandatory.add(DIGITS[random.nextInt(DIGITS.length)])
        if (config.includeSymbols) mandatory.add(SYMBOLS[random.nextInt(SYMBOLS.length)])

        for (i in 0 until (config.length - mandatory.size)) {
            password.append(charPool[random.nextInt(charPool.length)])
        }

        // Insert mandatory characters randomly
        for (char in mandatory) {
            val insertPos = if (password.isEmpty()) 0 else random.nextInt(password.length + 1)
            password.insert(insertPos, char)
        }

        return password.toString()
    }

    /**
     * Evaluates password strength and entropy.
     */
    fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, "Empty", 0.0, listOf("Enter or generate a password"))
        }

        var poolSize = 0
        val hasLower = password.any { it in LOWERCASE }
        val hasUpper = password.any { it in UPPERCASE }
        val hasDigit = password.any { it in DIGITS }
        val hasSymbol = password.any { it in SYMBOLS }

        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += SYMBOLS.length

        if (poolSize == 0) poolSize = 26

        val entropyBits = password.length * log2(poolSize.toDouble())
        val feedback = mutableListOf<String>()

        if (password.length < 12) feedback.add("Length should be at least 12 characters")
        if (!hasUpper) feedback.add("Add uppercase letters (A-Z)")
        if (!hasLower) feedback.add("Add lowercase letters (a-z)")
        if (!hasDigit) feedback.add("Add numbers (0-9)")
        if (!hasSymbol) feedback.add("Add symbols (!@#$)")

        val score = (entropyBits / 1.28).toInt().coerceIn(0, 100)
        val label = when {
            score >= 80 -> "Excellent"
            score >= 60 -> "Strong"
            score >= 40 -> "Medium"
            else -> "Weak"
        }

        return PasswordStrength(score, label, entropyBits, feedback)
    }
}
