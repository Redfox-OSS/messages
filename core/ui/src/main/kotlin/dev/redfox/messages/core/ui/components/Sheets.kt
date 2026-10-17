package dev.redfox.messages.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

/**
 * Bottom sheet on Obsidian surfaces (Obsidian has none; PLAN.md §7.2 allows wrapping the stock
 * one here). Card-coloured, large top corners, a small grab handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ObsidianTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = ObSpacing.M, bottom = ObSpacing.S)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.borderStrong)
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = ObSpacing.L)) {
            if (title != null) {
                Text(
                    title,
                    style = ObsidianTheme.typography.title,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = ScreenGutter, vertical = ObSpacing.S),
                )
            }
            content()
        }
    }
}

/** One tappable action row in an [ObSheet]. */
@Composable
fun SheetAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = ObsidianTheme.colors
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, haptic = HapticFeedbackType.ContextClick, onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.L),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (destructive) colors.error else colors.textSecondary)
        Text(label, style = ObsidianTheme.typography.bodyLarge, color = tint)
    }
}
