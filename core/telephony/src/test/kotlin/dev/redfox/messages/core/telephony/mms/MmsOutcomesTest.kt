package dev.redfox.messages.core.telephony.mms

import android.telephony.SmsManager
import dev.redfox.messages.core.mms.pdu.PduHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

class MmsOutcomesTest {

    private fun ok(status: Int?, matches: Boolean = true) =
        sendOutcome(resultOk = true, resultCode = -1, httpStatus = 200, responseStatus = status, transactionMatches = matches)

    private fun error(code: Int, http: Int = 0) =
        sendOutcome(resultOk = false, resultCode = code, httpStatus = http, responseStatus = null, transactionMatches = false)

    @Test
    fun `an OK confirmation for this message is sent`() = assertEquals(SendOutcome.Sent, ok(PduHeaders.RESPONSE_STATUS_OK))

    @Test
    fun `no confirmation is not sent`() = assertEquals(SendOutcome.Failed, ok(null))

    @Test
    fun `a confirmation for another message is not sent`() =
        assertEquals(SendOutcome.Failed, ok(PduHeaders.RESPONSE_STATUS_OK, matches = false))

    @Test
    fun `a transient carrier status is retried, a permanent one fails`() {
        assertEquals(SendOutcome.Retry, ok(0xC0))
        assertEquals(SendOutcome.Failed, ok(0xE0))
    }

    @Test
    fun `connection trouble is retried`() {
        assertEquals(SendOutcome.Retry, error(SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS))
        assertEquals(SendOutcome.Retry, error(SmsManager.MMS_ERROR_IO_ERROR))
        assertEquals(SendOutcome.Retry, error(SmsManager.MMS_ERROR_HTTP_FAILURE, http = 503))
    }

    @Test
    fun `a 404, bad APN or configuration error fails at once`() {
        assertEquals(SendOutcome.Failed, error(SmsManager.MMS_ERROR_HTTP_FAILURE, http = 404))
        assertEquals(SendOutcome.Failed, error(SmsManager.MMS_ERROR_INVALID_APN))
        assertEquals(SendOutcome.Failed, error(SmsManager.MMS_ERROR_CONFIGURATION_ERROR))
    }

    @Test
    fun `recipients lose separators, emails stay`() {
        assertEquals("+919876543210", cleanRecipient(" +91 98765-43210 "))
        assertEquals("09876543210", cleanRecipient("(098) 765 43210"))
        assertEquals("a.b@example.com", cleanRecipient("a.b@example.com"))
    }
}
