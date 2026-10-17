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
    fun `an OK confirmation for this message is sent`() = assertEquals(MmsOutcome.Done, ok(PduHeaders.RESPONSE_STATUS_OK))

    @Test
    fun `no confirmation is not sent`() = assertEquals(MmsOutcome.Failed, ok(null))

    @Test
    fun `a confirmation for another message is not sent`() =
        assertEquals(MmsOutcome.Failed, ok(PduHeaders.RESPONSE_STATUS_OK, matches = false))

    @Test
    fun `a transient carrier status is retried, a permanent one fails`() {
        assertEquals(MmsOutcome.Retry, ok(0xC0))
        assertEquals(MmsOutcome.Failed, ok(0xE0))
    }

    @Test
    fun `connection trouble is retried`() {
        assertEquals(MmsOutcome.Retry, error(SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS))
        assertEquals(MmsOutcome.Retry, error(SmsManager.MMS_ERROR_IO_ERROR))
        assertEquals(MmsOutcome.Retry, error(SmsManager.MMS_ERROR_HTTP_FAILURE, http = 503))
    }

    @Test
    fun `a 404, bad APN or configuration error fails at once`() {
        assertEquals(MmsOutcome.Failed, error(SmsManager.MMS_ERROR_HTTP_FAILURE, http = 404))
        assertEquals(MmsOutcome.Failed, error(SmsManager.MMS_ERROR_INVALID_APN))
        assertEquals(MmsOutcome.Failed, error(SmsManager.MMS_ERROR_CONFIGURATION_ERROR))
    }

    @Test
    fun `a download counts only when it parsed and its status is OK`() {
        assertEquals(MmsOutcome.Done, downloadOutcome(true, -1, 200, parsed = true, retrieveStatus = null))
        assertEquals(MmsOutcome.Done, downloadOutcome(true, -1, 200, parsed = true, retrieveStatus = PduHeaders.RETRIEVE_STATUS_OK))
        assertEquals(MmsOutcome.Failed, downloadOutcome(true, -1, 200, parsed = false, retrieveStatus = null))
        assertEquals(MmsOutcome.Retry, downloadOutcome(true, -1, 200, parsed = true, retrieveStatus = 0xC1))
        assertEquals(MmsOutcome.Failed, downloadOutcome(true, -1, 200, parsed = true, retrieveStatus = 0xE1))
        assertEquals(MmsOutcome.Retry, downloadOutcome(false, SmsManager.MMS_ERROR_IO_ERROR, 0, parsed = false, retrieveStatus = null))
    }

    @Test
    fun `failures are explained in plain words`() {
        assertEquals(
            "Couldn't connect to your carrier's MMS service. Your plan may not include MMS.",
            failureReason(false, SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS, 0, null),
        )
        assertEquals("Your carrier's MMS server didn't accept it (HTTP 403).", failureReason(false, SmsManager.MMS_ERROR_HTTP_FAILURE, 403, null))
        assertEquals("Your carrier didn't confirm the message.", failureReason(true, -1, 200, null))
    }

    @Test
    fun `Jio SIMs are known to have no MMS`() {
        assertEquals("Jio", carrierWithoutMms("405840"))
        assertEquals("Jio", carrierWithoutMms("405861"))
        assertEquals(null, carrierWithoutMms("40474")) // BSNL
        assertEquals(null, carrierWithoutMms("405875"))
        assertEquals(null, carrierWithoutMms(""))
    }

    @Test
    fun `recipients lose separators, emails stay`() {
        assertEquals("+919876543210", cleanRecipient(" +91 98765-43210 "))
        assertEquals("09876543210", cleanRecipient("(098) 765 43210"))
        assertEquals("a.b@example.com", cleanRecipient("a.b@example.com"))
    }
}
