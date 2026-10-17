package dev.redfox.messages.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    private fun otp(text: String) = OtpExtractor.extract(text)

    @Test
    fun `code before the wording`() =
        assertEquals("482913", otp("482913 is your OTP for login. Do not share it with anyone."))

    @Test
    fun `code after the wording`() =
        assertEquals("7310", otp("Your verification code is 7310. Valid for 10 minutes."))

    @Test
    fun `google style prefixed code`() =
        assertEquals("123456", otp("G-123456 is your Google verification code."))

    @Test
    fun `amount and masked account are skipped`() = assertEquals(
        "558201",
        otp("OTP for txn of Rs.4999 on card XX1234 is 558201. Valid 5 mins. Do not share."),
    )

    @Test
    fun `nearest number to the wording wins`() = assertEquals(
        "90417",
        otp("Order 20261017 placed. Your one-time password is 90417 for delivery."),
    )

    @Test
    fun `transaction without otp wording is not an otp`() =
        assertNull(otp("Rs.1,250.00 debited from A/c XX1234 on 16-10-26 via UPI. Ref 6123"))

    @Test
    fun `otp wording without a code is not an otp`() =
        assertNull(otp("Never share your OTP with anyone, including bank staff."))

    @Test
    fun `decimal amounts are not codes`() =
        assertNull(otp("Your OTP request for Rs 1,250.50 failed."))
}
