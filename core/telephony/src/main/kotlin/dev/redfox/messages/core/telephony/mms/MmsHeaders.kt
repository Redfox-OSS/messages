package dev.redfox.messages.core.telephony.mms

import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager

/**
 * The two HTTP headers an MMS server uses to tell which phone is calling: `User-Agent` and
 * `x-wap-profile` (a URL describing the handset). Many carriers' MMS servers turn requests
 * without them away.
 *
 * The system's MMS service takes them from the carrier config, but on phones whose maker ships
 * its own messaging app (Samsung, for one) that config leaves both empty: the maker's app sets
 * its own. So when the carrier config has none, we supply what the maker's app would send.
 */
internal data class MmsHeaders(val userAgent: String?, val uaProfUrl: String?) {

    /** As `configOverrides` for [SmsManager]'s MMS calls; null when there's nothing to add. */
    fun toBundle(): Bundle? {
        if (userAgent == null && uaProfUrl == null) return null
        return Bundle().apply {
            userAgent?.let { putString(SmsManager.MMS_CONFIG_USER_AGENT, it) }
            uaProfUrl?.let { putString(SmsManager.MMS_CONFIG_UA_PROF_URL, it) }
        }
    }

    companion object {
        /** AOSP Messaging's defaults, for phones whose maker we don't know. */
        const val AOSP_USER_AGENT = "Android-Mms/2.0"
        const val AOSP_UA_PROF_URL = "http://www.gstatic.com/android/sms/mms_ua_profile.xml"

        /**
         * Headers to add for [manufacturer]'s [model], given what the carrier config already has.
         * A header the carrier config sets is left alone (null here: nothing to override).
         */
        fun overrides(
            carrierUserAgent: String?,
            carrierUaProfUrl: String?,
            manufacturer: String = Build.MANUFACTURER,
            model: String = Build.MODEL,
        ): MmsHeaders {
            val samsung = manufacturer.equals("samsung", ignoreCase = true)
            val userAgent = if (samsung) "SAMSUNG-ANDROID-MMS/$model" else AOSP_USER_AGENT
            val uaProfUrl = if (samsung) "http://wap.samsungmobile.com/uaprof/$model.xml" else AOSP_UA_PROF_URL
            return MmsHeaders(
                userAgent = userAgent.takeIf { carrierUserAgent.isNullOrBlank() },
                uaProfUrl = uaProfUrl.takeIf { carrierUaProfUrl.isNullOrBlank() },
            )
        }
    }
}
