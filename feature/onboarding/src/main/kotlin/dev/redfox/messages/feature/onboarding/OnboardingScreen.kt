package dev.redfox.messages.feature.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.model.DeviceSetupStatus
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObCheckbox
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.PrimaryButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.effects.ambientFloat
import dev.redfox.obsidian.effects.breathingGlow
import dev.redfox.obsidian.motion.ExpandEnter
import dev.redfox.obsidian.motion.CollapseExit
import dev.redfox.obsidian.motion.RevealColumn
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import androidx.compose.animation.AnimatedVisibility
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingKey : NavKey

private val RuntimePermissions = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.POST_NOTIFICATIONS,
)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val refresh = { viewModel.dispatch(OnboardingIntent.Refresh) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.RequestDefaultSmsRole -> {
                val roles = context.getSystemService(RoleManager::class.java)
                roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
            OnboardingEffect.RequestRuntimePermissions -> permissionsLauncher.launch(RuntimePermissions)
            OnboardingEffect.RequestBatteryExemption -> batteryLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            )
            OnboardingEffect.Finished -> onFinished()
        }
    }

    OnboardingScreen(state = state, onIntent = viewModel::dispatch)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = ScreenGutter)
                .padding(top = 56.dp, bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroBadge()
            Spacer(Modifier.height(ObSpacing.XL))
            Text(
                "Messages",
                style = type.title,
                color = colors.textPrimary,
                modifier = Modifier.revealOnEnter(1)
            )
            Spacer(Modifier.height(ObSpacing.XS))
            Text(
                "Fast, private SMS & MMS. Three quick steps and you're in.",
                style = type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.revealOnEnter(2)
            )
            Spacer(Modifier.height(ObSpacing.XXL))
            RevealColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ObSpacing.M)
            ) {
                SectionLabel("Setup")
                SetupCard(status = state.status, onIntent = onIntent)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = ScreenGutter, vertical = ObSpacing.L),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryButton(
                text = "Continue",
                onClick = { onIntent(OnboardingIntent.ContinueClicked) },
                enabled = state.canContinue,
            )
            AnimatedVisibility(visible = !state.status.isDefaultSmsApp, enter = ExpandEnter, exit = CollapseExit) {
                Text(
                    "Set Messages as your default SMS app to continue",
                    style = type.label,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = ObSpacing.M)
                )
            }
        }
    }
}

@Composable
private fun HeroBadge() {
    val colors = ObsidianTheme.colors
    // The EmptyState badge recipe: an idle hero that floats while its halo breathes.
    Box(
        Modifier
            .revealOnEnter(0)
            .ambientFloat()
            .breathingGlow(colors.accent, CircleShape)
            .size(88.dp)
            .clip(CircleShape)
            .background(colors.accentContainer)
            .border(1.dp, colors.accentBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_chat),
            contentDescription = null,
            tint = colors.accentLight,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun SetupCard(status: DeviceSetupStatus, onIntent: (OnboardingIntent) -> Unit) {
    ObCard(Modifier.fillMaxWidth()) {
        SetupRow(
            icon = R.drawable.ic_sms,
            title = "Default SMS app",
            pending = "Required to send and receive texts",
            done = status.isDefaultSmsApp,
            onClick = { onIntent(OnboardingIntent.DefaultSmsClicked) },
        )
        ObDivider(Modifier.padding(vertical = ObSpacing.M))
        SetupRow(
            icon = R.drawable.ic_contacts,
            title = "Contacts & notifications",
            pending = "Names, and alerts for new texts",
            done = status.hasRuntimePermissions,
            onClick = { onIntent(OnboardingIntent.PermissionsClicked) },
        )
        ObDivider(Modifier.padding(vertical = ObSpacing.M))
        SetupRow(
            icon = R.drawable.ic_battery_full,
            title = "Run in background",
            pending = "So texts are never delayed",
            done = status.isIgnoringBatteryOptimizations,
            onClick = { onIntent(OnboardingIntent.BatteryClicked) },
        )
    }
}

@Composable
private fun SetupRow(
    @DrawableRes icon: Int,
    title: String,
    pending: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    ObListRow(
        title = title,
        icon = icon,
        subtitle = if (done) "Done" else pending,
        iconTint = if (done) ObsidianTheme.colors.accentLight else ObsidianTheme.colors.textSecondary,
        titleStyleLarge = true,
        onClick = onClick,
        trailing = { ObCheckbox(checked = done, onCheckedChange = null) },
    )
}
