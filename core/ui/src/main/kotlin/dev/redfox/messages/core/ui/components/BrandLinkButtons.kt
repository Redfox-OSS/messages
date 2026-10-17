package dev.redfox.messages.core.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.format.BrandLink
import dev.redfox.messages.core.ui.format.LinkBrand
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LogoSize = 20.dp

/**
 * "Open in YouTube"-style buttons under a message that links to a well-known service. The
 * message itself keeps its highlighted link; these are a bigger, recognisable shortcut.
 */
@Composable
fun BrandLinkButtons(
    links: List<BrandLink>,
    onOpen: (BrandLink) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(ObSpacing.XS),
        horizontalAlignment = horizontalAlignment,
    ) {
        links.forEach { link -> BrandLinkButton(link, onClick = { onOpen(link) }) }
    }
}

@Composable
private fun BrandLinkButton(link: BrandLink, onClick: () -> Unit) {
    val colors = ObsidianTheme.colors
    Row(
        Modifier
            .widthIn(max = 300.dp)
            .pressable(pressedScale = 0.96f, haptic = HapticFeedbackType.ContextClick, onClick = onClick)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.borderStrong, CircleShape)
            .padding(start = ObSpacing.XS, end = ObSpacing.M, top = ObSpacing.XS, bottom = ObSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        BrandLogo(link.brand, LogoSize + 8.dp)
        Text(
            "Open in ${link.brand.name}",
            style = ObsidianTheme.typography.labelStrong,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** The brand's own app icon when it's installed; otherwise its initial on the brand colour. */
@Composable
private fun BrandLogo(brand: LinkBrand, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(AppIcons.cached(brand.packageName), brand.packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { AppIcons.load(context, brand.packageName, px) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        val tile = Color(brand.color)
        Box(Modifier.size(size).clip(CircleShape).background(tile), contentAlignment = Alignment.Center) {
            Text(
                brand.name.take(1),
                style = ObsidianTheme.typography.labelStrong,
                color = if (tile.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}

/** Installed apps' launcher icons, rendered once per package and kept for the session. */
private object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(32)
    private val missing = HashSet<String>()

    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        synchronized(missing) { if (packageName in missing) return null }
        return try {
            val pm = context.packageManager
            if (!pm.getApplicationInfo(packageName, 0).enabled) error("disabled")
            pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
                .also { cache.put(packageName, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            synchronized(missing) { missing += packageName }
            null
        } catch (e: IllegalStateException) {
            synchronized(missing) { missing += packageName }
            null
        }
    }
}
