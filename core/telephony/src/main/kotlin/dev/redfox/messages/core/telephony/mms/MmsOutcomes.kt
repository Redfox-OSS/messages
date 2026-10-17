package dev.redfox.messages.core.telephony.mms

import android.telephony.SmsManager
import dev.redfox.messages.core.mms.pdu.PduHeaders

/** What to do with an MMS send or download once the platform reports back. */
internal enum class MmsOutcome { Done, Retry, Failed }

/**
 * Reads the platform's send result the way AOSP Messaging does.
 *
 * - [resultOk]: the platform got a response from the carrier's MMS server. It only counts as
 *   sent if that response is an M-Send.conf for *this* message ([transactionMatches]) saying OK;
 *   a missing or unreadable confirmation is a failure, not a success.
 * - Otherwise [resultCode] is an `SmsManager.MMS_ERROR_*` (see [errorOutcome]).
 */
internal fun sendOutcome(
    resultOk: Boolean,
    resultCode: Int,
    httpStatus: Int,
    responseStatus: Int?,
    transactionMatches: Boolean,
): MmsOutcome = when {
    resultOk -> when {
        responseStatus == null || !transactionMatches -> MmsOutcome.Failed
        responseStatus == PduHeaders.RESPONSE_STATUS_OK -> MmsOutcome.Done
        responseStatus in TRANSIENT_STATUS -> MmsOutcome.Retry
        else -> MmsOutcome.Failed
    }
    else -> errorOutcome(resultCode, httpStatus)
}

/**
 * Reads a download result: the platform fetched something ([resultOk]) that parsed as an
 * M-Retrieve.conf ([parsed]) whose retrieve status (absent means OK) says it's the message.
 */
internal fun downloadOutcome(
    resultOk: Boolean,
    resultCode: Int,
    httpStatus: Int,
    parsed: Boolean,
    retrieveStatus: Int?,
): MmsOutcome = when {
    resultOk -> when {
        !parsed -> MmsOutcome.Failed
        retrieveStatus == null || retrieveStatus == 0 || retrieveStatus == PduHeaders.RETRIEVE_STATUS_OK -> MmsOutcome.Done
        retrieveStatus in TRANSIENT_STATUS -> MmsOutcome.Retry
        else -> MmsOutcome.Failed
    }
    else -> errorOutcome(resultCode, httpStatus)
}

/** Connection and I/O trouble is temporary; a 404 from the server, or a bad APN or configuration, is not. */
private fun errorOutcome(resultCode: Int, httpStatus: Int): MmsOutcome = when (resultCode) {
    SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS, SmsManager.MMS_ERROR_IO_ERROR, SmsManager.MMS_ERROR_RETRY -> MmsOutcome.Retry
    SmsManager.MMS_ERROR_HTTP_FAILURE -> if (httpStatus == HTTP_NOT_FOUND) MmsOutcome.Failed else MmsOutcome.Retry
    else -> MmsOutcome.Failed
}

/** Waits before each automatic retry: about 4 minutes in all, inside the 10 minutes a send may show "Sending". */
internal val RETRY_DELAYS_MILLIS = listOf(20_000L, 60_000L, 180_000L)

/**
 * Why a send failed, in words for the person sending it. [resultCode] is the platform's last
 * result; [responseStatus] the carrier's answer when it gave one.
 */
internal fun failureReason(resultOk: Boolean, resultCode: Int, httpStatus: Int, responseStatus: Int?): String = when {
    resultOk && responseStatus == null -> "Your carrier didn't confirm the message."
    resultOk -> "Your carrier turned the message down."
    else -> when (resultCode) {
        SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR ->
            "This SIM's MMS settings (APN) are missing or wrong."
        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ->
            "Couldn't connect to your carrier's MMS service. Your plan may not include MMS."
        SmsManager.MMS_ERROR_HTTP_FAILURE ->
            "Your carrier's MMS server didn't accept it" + (if (httpStatus > 0) " (HTTP $httpStatus)." else ".")
        SmsManager.MMS_ERROR_IO_ERROR -> "The connection to your carrier's MMS server didn't go through."
        SmsManager.MMS_ERROR_RETRY -> "Your carrier's MMS service is busy. Try again later."
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> "No mobile network for MMS right now."
        SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID, SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION -> "That SIM isn't available."
        SmsManager.MMS_ERROR_DATA_DISABLED -> "Mobile data is off. Turn it on to send MMS."
        SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> "Your carrier has turned MMS off for this SIM."
        else -> "The message couldn't be sent."
    }
}

/**
 * Networks that offer no MMS at all, by MCC+MNC. Their SIMs have no MMS access point, so a
 * send can only hang on "Sending" until the platform gives up; saying so at once is better.
 * Jio: 405-840 and 405-854…874.
 */
internal fun carrierWithoutMms(mccMnc: String): String? {
    if (mccMnc.length < 5 || !mccMnc.startsWith("405")) return null
    val mnc = mccMnc.substring(3).toIntOrNull() ?: return null
    return if (mnc == 840 || mnc in 854..874) "Jio" else null
}

/**
 * A recipient as the MMS server expects it: phone numbers without spaces, dashes or brackets
 * ("+91 98765-43210" → "+919876543210"); email addresses as they are.
 */
internal fun cleanRecipient(address: String): String {
    val trimmed = address.trim()
    if ('@' in trimmed) return trimmed
    return trimmed.filter { it.isLetterOrDigit() || it == '+' || it == '*' || it == '#' }
}

/** X-Mms-Response-Status / X-Mms-Retrieve-Status "transient failure" codes (0xC0–0xDF), OMA MMS ENC §7.3. */
private val TRANSIENT_STATUS = 0xC0..0xDF
private const val HTTP_NOT_FOUND = 404
