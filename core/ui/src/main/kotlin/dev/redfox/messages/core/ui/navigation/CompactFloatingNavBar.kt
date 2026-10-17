package dev.redfox.messages.core.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.redfox.obsidian.effects.ObGlows
import dev.redfox.obsidian.effects.glassEffect
import dev.redfox.obsidian.effects.glow
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.navigation.FloatingNavItem
import dev.redfox.obsidian.navigation.NavIconMotion
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Obsidian's floating frosted pill nav (same glass, border, elevation glow, liquid indicator and
 * per-icon [NavIconMotion]s as `dev.redfox.obsidian.navigation.FloatingNavBar`), plus a
 * **compact** state: while content scrolls toward its end the pill narrows to an icons-tight
 * capsule and lowers; scrolling back expands it. It never hides; tabs stay tappable.
 *
 * Built to the compact-on-scroll recipe's performance rules:
 * 1. One animated `shrink` (0 full → 1 compact), read only in layout / placement / draw lambdas,
 *    so the morph never recomposes the bar.
 * 2. The slot keeps the full height, so the page underneath never re-measures — and
 *    `floatingNavClearance()` stays exact.
 * 3. The halo is placed with `placeWithLayer`, so tab switches don't re-measure either.
 *
 * The morph is a spring (Obsidian: everything interactive is interruptible), not a tween.
 */
@Composable
fun CompactFloatingNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    /** A State, not a Boolean, so only the bar (not its caller) recomposes when it flips. */
    compact: State<Boolean>,
    modifier: Modifier = Modifier,
    /** A plain action at the end of the bar (e.g. new message): never selected, no halo. */
    action: FloatingNavItem? = null,
    onAction: () -> Unit = {},
    /** A separate accented round button beside the bar (e.g. search). */
    pill: FloatingNavItem? = null,
    onPill: () -> Unit = {},
) {
    val colors = ObsidianTheme.colors
    val slots = (items.size + if (action != null) 1 else 0).coerceAtLeast(1)
    val shrink = animateFloatAsState(if (compact.value) 1f else 0f, ObMotion.gentle(), label = "navCompact")

    val position = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.58f, stiffness = 300f))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = ScreenGutter, end = ScreenGutter, bottom = BottomGap),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val pillRoom = if (pill != null) FullHeight + PillGap else 0.dp
        val fullWidth = minOf(maxWidth - pillRoom, MaxWidth)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PillGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val t = shrink.value
                        val slotHeight = FullHeight.roundToPx()
                        val compactWidth = (CompactSlot * slots + InnerPadding * 2).toPx()
                        val width = lerp(fullWidth.toPx(), compactWidth, t).roundToInt()
                        val height = lerp(FullHeight.toPx(), CompactHeight.toPx(), t).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        // Fixed full-height slot; the capsule sits on its bottom edge.
                        layout(width, slotHeight) { placeable.place(0, slotHeight - height) }
                    }
                    .glow(Color.Black, CircleShape, ObGlows.Elevation)
                    .clip(CircleShape)
                    .glassEffect(colors.navScrim)
                    .border(1.dp, colors.borderStrong, CircleShape)
                    .padding(horizontal = InnerPadding),
            ) {
                if (selectedIndex >= 0) LiquidHalo(position, slots, shrink)
                Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    items.forEachIndexed { index, item ->
                        NavItem(
                            item = item,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (action != null) {
                        NavItem(item = action, selected = false, onClick = onAction, isAction = true, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (pill != null) AccentPill(pill, shrink, onPill)
        }
    }
}

/** The separate round button: Obsidian's accent action (solid accent, soft glow), shrinking with the bar. */
@Composable
private fun AccentPill(item: FloatingNavItem, shrink: State<Float>, onClick: () -> Unit) {
    val colors = ObsidianTheme.colors
    val haptics = LocalHapticFeedback.current
    val motion = remember { IconMotion() }
    var plays by remember { mutableIntStateOf(0) }
    LaunchedEffect(plays) { if (plays > 0) motion.play(item.motion) }
    Box(
        Modifier
            .layout { measurable, _ ->
                val slotHeight = FullHeight.roundToPx()
                val size = lerp(FullHeight.toPx(), CompactHeight.toPx(), shrink.value).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                layout(size, slotHeight) { placeable.place(0, slotHeight - size) }
            }
            .glow(colors.accent, CircleShape, ObGlows.Action)
            .clip(CircleShape)
            .background(colors.accent)
            .semantics { role = Role.Button }
            .pressable(pressedScale = 0.88f) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                plays++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(item.icon),
            contentDescription = item.label,
            tint = colors.onAccent,
            modifier = Modifier.graphicsLayer {
                scaleX = motion.scale.value * motion.squashX
                scaleY = motion.scale.value * motion.squashY
                rotationZ = motion.rotation.value
            },
        )
    }
}

/**
 * The selected-tab halo: one shared pill that fills the tab's slot (minus a small inset) and
 * travels between tabs, stretching with its velocity like a dragged drop of liquid. It keeps
 * its pill shape at both sizes: the height follows the bar, the width follows the slot.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiquidHalo(
    position: Animatable<Float, AnimationVector1D>,
    count: Int,
    shrink: State<Float>,
) {
    val colors = ObsidianTheme.colors
    Box(
        Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                // Sized in layout from the live bar width and shrink: no recomposition per frame.
                val slot = constraints.maxWidth.toFloat() / count
                val height = lerp(HaloHeight.toPx(), CompactHaloHeight.toPx(), shrink.value).roundToInt()
                val width = (slot - HaloSideInset.toPx() * 2).roundToInt().coerceAtLeast(height)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (slot * position.value + (slot - width) / 2f).roundToInt()
                    placeable.placeWithLayer(x, (constraints.maxHeight - height) / 2) {
                        // Velocity in tabs/second. A pill is already wide, so it stretches less
                        // than a circle would (≤25%) and never runs over its neighbours.
                        val stretch = (abs(position.velocity) / 18f).coerceAtMost(0.25f)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.3f
                    }
                }
            }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .glow(colors.accentGlow, CircleShape, ObGlows.Halo)
                .clip(CircleShape)
                .background(colors.accentContainer)
                .border(1.dp, colors.accentLight.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
private fun NavItem(
    item: FloatingNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** An action, not a tab: every tap plays the motion and runs [onClick]. */
    isAction: Boolean = false,
) {
    val colors = ObsidianTheme.colors
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        if (selected) colors.textPrimary else colors.textSecondary.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "navTint",
    )

    // Replays on every selection, and on re-tapping the current tab.
    var plays by remember { mutableIntStateOf(0) }
    val initial = remember { booleanArrayOf(true) }
    LaunchedEffect(selected) {
        if (initial[0]) initial[0] = false else if (selected) plays++
    }
    val motion = remember { IconMotion() }
    LaunchedEffect(plays) { if (plays > 0) motion.play(item.motion) }

    Box(
        modifier
            .fillMaxHeight()
            .semantics {
                this.selected = selected
                role = if (isAction) Role.Button else Role.Tab
            }
            .pressable(pressedScale = 0.86f, role = if (isAction) Role.Button else Role.Tab) {
                when {
                    isAction -> {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        plays++
                        onClick()
                    }
                    selected -> {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        plays++
                    }
                    else -> {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(item.icon),
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = motion.scale.value * motion.squashX
                scaleY = motion.scale.value * motion.squashY
                rotationZ = motion.rotation.value
                translationY = motion.lift.value * density
            },
        )
    }
}

/** Per-icon transform channels — the same choreography as Obsidian's nav icons. */
private class IconMotion {
    val scale = Animatable(1f)
    val rotation = Animatable(0f)
    /** In dp; negative lifts the icon. */
    val lift = Animatable(0f)
    /** 1 = fully squashed (wide and short), 0 = at rest; overshoots for a jelly wobble. */
    val squash = Animatable(0f)

    val squashX get() = 1f + 0.26f * squash.value
    val squashY get() = 1f - 0.22f * squash.value

    suspend fun play(kind: NavIconMotion) = coroutineScope {
        // Shared beat: a quick dip, then an overshooting pop back to size.
        launch {
            scale.snapTo(0.7f)
            scale.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 420f))
        }
        when (kind) {
            NavIconMotion.Pop -> Unit
            NavIconMotion.Shake -> launch {
                rotation.snapTo(0f)
                rotation.animateTo(0f, keyframes {
                    durationMillis = 720
                    -24f at 70
                    20f at 160
                    -16f at 250
                    12f at 340
                    -8f at 430
                    4f at 530
                    -2f at 620
                })
            }
            NavIconMotion.Flip -> launch {
                rotation.snapTo(0f)
                rotation.animateTo(180f, spring(dampingRatio = 0.5f, stiffness = 220f))
                rotation.snapTo(0f)
            }
            NavIconMotion.Press -> launch {
                launch {
                    lift.snapTo(0f)
                    lift.animateTo(2.5f, tween(70))
                    lift.animateTo(-4f, spring(dampingRatio = 1f, stiffness = 900f))
                    lift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f))
                }
                rotation.snapTo(-28f)
                rotation.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f))
            }
            NavIconMotion.Jelly -> launch {
                squash.snapTo(1f)
                squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 360f))
            }
            NavIconMotion.Turn -> launch {
                rotation.snapTo(0f)
                rotation.animateTo(180f, spring(dampingRatio = 0.55f, stiffness = 110f))
                rotation.snapTo(0f)
            }
        }
    }
}

// Full size matches Obsidian's FloatingNavBar exactly, so floatingNavClearance() stays correct.
private val FullHeight = 64.dp
private val BottomGap = 12.dp
private val MaxWidth = 420.dp
/** Space between the bar and the separate pill. */
private val PillGap = 10.dp
private val InnerPadding = 5.dp
/** Pill height in the full bar: 5dp clear of the 64dp capsule's edges. */
private val HaloHeight = 54.dp
/** Gap between the pill and its slot's edges. */
private val HaloSideInset = 2.dp

private val CompactHeight = 52.dp
/** Per-tab width when compact: wide enough that the selected pill still reads as a pill (58 × 44). */
private val CompactSlot = 62.dp
/** Pill height in the 52dp compact capsule: 4dp breathing room top and bottom. */
private val CompactHaloHeight = 44.dp
