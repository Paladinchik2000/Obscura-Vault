package com.obscura.security

import androidx.annotation.Keep
import java.security.SecureRandom
import kotlin.math.log2

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

    /** Display text for each level lives in string resources (see ui/common/Labels.kt). */
    enum class StrengthLevel { EMPTY, WEAK, MEDIUM, STRONG, EXCELLENT }

    data class PasswordStrength(
        val score: Int, // 0 to 100
        val level: StrengthLevel,
        val entropyBits: Double
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
            return PasswordStrength(0, StrengthLevel.EMPTY, 0.0)
        }

        var poolSize = 0
        if (password.any { it in LOWERCASE }) poolSize += 26
        if (password.any { it in UPPERCASE }) poolSize += 26
        if (password.any { it in DIGITS }) poolSize += 10
        if (password.any { it in SYMBOLS }) poolSize += SYMBOLS.length

        if (poolSize == 0) poolSize = 26

        val entropyBits = password.length * log2(poolSize.toDouble())
        val score = (entropyBits / 1.28).toInt().coerceIn(0, 100)
        val level = when {
            score >= 80 -> StrengthLevel.EXCELLENT
            score >= 60 -> StrengthLevel.STRONG
            score >= 40 -> StrengthLevel.MEDIUM
            else -> StrengthLevel.WEAK
        }

        return PasswordStrength(score, level, entropyBits)
    }
}
