package dev.redfox.messages.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug builds only: marks onboarding done, so the debug app can open its inbox and sync
 * without becoming the default SMS app (reading needs only READ_SMS, granted by `adb install -g`):
 *
 * ```
 * adb shell am broadcast -n dev.redfox.messages.debug/dev.redfox.messages.debug.DebugSkipOnboardingReceiver
 * ```
 */
@AndroidEntryPoint
class DebugSkipOnboardingReceiver : BroadcastReceiver() {

    @Inject lateinit var completeOnboarding: CompleteOnboardingUseCase
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                completeOnboarding()
            } finally {
                pending.finish()
            }
        }
    }
}
