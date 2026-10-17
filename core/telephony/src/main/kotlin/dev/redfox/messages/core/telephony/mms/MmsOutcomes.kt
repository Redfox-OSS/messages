package dev.redfox.messages.core.telephony.mms

import android.telephony.SmsManager
import dev.redfox.messages.core.mms.pdu.PduHeaders

/** What to do with an outgoing MMS once the platform reports back. */
internal enum class SendOutcome { Sent, Retry, Failed }

/**
 * Reads the platform's send result the way AOSP Messaging does.
 *
 * - [resultOk]: the platform got a response from the carrier's MMS server. It only counts as
 *   sent if that response is an M-Send.conf for *this* message ([transactionMatches]) saying OK;
 *   a missing or unreadable confirmation is a failure, not a success.
 * - Otherwise [resultCode] is an `SmsManager.MMS_ERROR_*`: connection and I/O trouble is
 *   temporary (retry); a 404 from the server, or a bad APN or configuration, is not.
 */
internal fun sendOutcome(
    resultOk: Boolean,
    resultCode: Int,
    httpStatus: Int,
    responseStatus: Int?,
    transactionMatches: Boolean,
): SendOutcome = when {
    resultOk -> when {
        responseStatus == null || !transactionMatches -> SendOutcome.Failed
        responseStatus == PduHeaders.RESPONSE_STATUS_OK -> SendOutcome.Sent
        responseStatus in TRANSIENT_RESPONSE -> SendOutcome.Retry
        else -> SendOutcome.Failed
    }
    resultCode == SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ||
        resultCode == SmsManager.MMS_ERROR_IO_ERROR ||
        resultCode == SmsManager.MMS_ERROR_RETRY -> SendOutcome.Retry
    resultCode == SmsManager.MMS_ERROR_HTTP_FAILURE -> if (httpStatus == HTTP_NOT_FOUND) SendOutcome.Failed else SendOutcome.Retry
    else -> SendOutcome.Failed
}

/** Waits before each automatic resend: about 4 minutes in all, inside the 10 minutes a send may show "Sending". */
internal val SEND_RETRY_DELAYS_MILLIS = listOf(20_000L, 60_000L, 180_000L)

/**
 * A recipient as the MMS server expects it: phone numbers without spaces, dashes or brackets
 * ("+91 98765-43210" → "+919876543210"); email addresses as they are.
 */
internal fun cleanRecipient(address: String): String {
    val trimmed = address.trim()
    if ('@' in trimmed) return trimmed
    return trimmed.filter { it.isLetterOrDigit() || it == '+' || it == '*' || it == '#' }
}

/** X-Mms-Response-Status "transient failure" codes (0xC0–0xDF), OMA MMS ENC §7.3.48. */
private val TRANSIENT_RESPONSE = 0xC0..0xDF
private const val HTTP_NOT_FOUND = 404
