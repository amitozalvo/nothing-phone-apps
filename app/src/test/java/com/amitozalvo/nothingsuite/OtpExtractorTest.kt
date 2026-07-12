package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.notifications.OtpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun extractsSimpleCode() {
        assertEquals("482913", OtpExtractor.extract("Your verification code is 482913"))
    }

    @Test
    fun extractsCodeWithKeywordOtp() {
        assertEquals("1234", OtpExtractor.extract("OTP: 1234. Do not share it."))
    }

    @Test
    fun extractsHyphenSplitCode() {
        assertEquals("123456", OtpExtractor.extract("Your code: 123-456"))
    }

    @Test
    fun extractsHebrewMessage() {
        assertEquals("905531", OtpExtractor.extract("קוד האימות שלך הוא 905531"))
    }

    @Test
    fun ignoresMessageWithoutKeyword() {
        assertNull(OtpExtractor.extract("Meet me at 1830 by the station"))
    }

    @Test
    fun ignoresBlank() {
        assertNull(OtpExtractor.extract(""))
        assertNull(OtpExtractor.extract(null))
    }

    @Test
    fun ignoresLongNumbers() {
        assertNull(OtpExtractor.extract("Your code request 123456789012 was received"))
    }
}
