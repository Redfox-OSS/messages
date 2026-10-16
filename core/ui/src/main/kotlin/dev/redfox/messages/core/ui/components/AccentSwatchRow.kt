package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.ui.theme.toObsidian
import dev.redfox.obsidian.effects.ObGlows
import dev.redfox.obsidian.effects.glow
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.popOnSelect
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * Accent picker: one swatch per Obsidian preset. The selected swatch gets the `Action` glow, a
 * ring, and pops as it becomes selected.
 */
@Composable
fun AccentSwatchRow(
    selected: AppAccent,
    onSelect: (AppAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = ObsidianTheme.colors.textPrimary
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppAccent.entries.forEach { accent ->
            val obsidian = accent.toObsidian()
            val isSelected = accent == selected
            Box(
                Modifier
                    .semantics {
                        contentDescription = accent.name
                        this.selected = isSelected
                    }
                    .popOnSelect(isSelected, amount = 0.12f)
                    .then(if (isSelected) Modifier.glow(obsidian.main, CircleShape, ObGlows.Action) else Modifier)
                    .pressable(pressedScale = 0.88f, role = Role.RadioButton, haptic = HapticFeedbackType.SegmentTick) {
                        onSelect(accent)
                    }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(obsidian.swatch)
                    .border(2.dp, if (isSelected) ring else Color.Transparent, CircleShape)
            )
        }
    }
}
