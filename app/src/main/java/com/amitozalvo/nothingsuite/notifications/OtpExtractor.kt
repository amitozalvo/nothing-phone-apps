package com.amitozalvo.nothingsuite.notifications

/**
 * Extracts one-time codes from notification text. Pure Kotlin — unit-tested.
 */
object OtpExtractor {

    // Keywords that suggest a message carries a verification code
    // (English + Hebrew, easily extended)
    private val KEYWORDS = listOf(
        "code", "otp", "verification", "verify", "passcode", "one-time",
        "one time", "2fa", "authentication", "pin",
        "קוד", "אימות", "חד פעמי", "חד-פעמי",
    )

    // 4–8 digits, optionally split by a single space/dash in the middle
    private val CODE_PATTERN = Regex("""(?<!\d)(\d{4,8}|\d{3}[- ]\d{3}|\d{4}[- ]\d{4})(?!\d)""")

    // Things that look like codes but aren't
    private val YEAR_RANGE = 1900..2100

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        if (KEYWORDS.none { it in lower }) return null

        val candidates = CODE_PATTERN.findAll(text)
            .map { it.groupValues[1].replace(Regex("[- ]"), "") }
            .filter { it.toIntOrNull() !in YEAR_RANGE || it.length != 4 }
            .toList()

        // Prefer the code closest to a keyword occurrence; fall back to first
        return candidates.firstOrNull()
    }
}
