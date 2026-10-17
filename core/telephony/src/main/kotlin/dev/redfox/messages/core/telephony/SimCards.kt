package dev.redfox.messages.core.telephony

import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.model.SimCard
import javax.inject.Inject
import javax.inject.Singleton

/** Active SIMs from [SubscriptionManager]. Needs READ_PHONE_STATE (granted with the SMS role). */
@Singleton
class SimCards @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun active(): List<SimCard> = try {
        context.getSystemService(SubscriptionManager::class.java)
            ?.activeSubscriptionInfoList
            .orEmpty()
            .map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.carrierName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" },
                    slotIndex = info.simSlotIndex,
                )
            }
    } catch (e: SecurityException) {
        Log.w(TAG, "No phone-state access; using the default SIM", e)
        emptyList()
    }

    fun defaultSmsSubscriptionId(): Int? =
        SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }

    private companion object {
        const val TAG = "SimCards"
    }
}
