package dev.redfox.messages.core.ui.format

import java.net.URI

/**
 * A well-known service whose links get an "Open in …" button under the message.
 *
 * [packageName] is its Android app: when installed, the button shows that app's own icon.
 * Tapping opens the link like any link tap (the app if it claims the link, else the browser).
 * [color] (ARGB) tints the letter tile shown when the app isn't installed.
 */
data class LinkBrand(
    val name: String,
    val packageName: String,
    val color: Long,
    /** Hosts (and their subdomains) that belong to the brand. */
    val hosts: List<String>,
    /** For shared hosts ("google.com/maps"), the path must start with one of these. */
    val pathPrefixes: List<String> = emptyList(),
)

/** A link in a message that belongs to a [LinkBrand]. */
data class BrandLink(val brand: LinkBrand, val url: String)

/**
 * Matches links in message text against well-known services (app stores, video, shopping,
 * food, travel, payments…). Pure Kotlin so it's unit-tested. Ordered: the first brand that
 * matches wins, so specific entries (Google Maps on google.com) come before generic ones.
 */
object LinkBrands {

    val All: List<LinkBrand> = listOf(
        LinkBrand("Play Store", "com.android.vending", 0xFF01875F, listOf("play.google.com", "play.app.goo.gl")),
        LinkBrand("YouTube", "com.google.android.youtube", 0xFFFF0033, listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
        LinkBrand(
            "Google Maps", "com.google.android.apps.maps", 0xFF34A853,
            listOf("maps.google.com", "maps.app.goo.gl", "google.com", "google.co.in", "goo.gl"),
            pathPrefixes = listOf("/maps"),
        ),
        LinkBrand("Google Drive", "com.google.android.apps.docs", 0xFF1A73E8, listOf("drive.google.com", "docs.google.com")),
        LinkBrand("Amazon", "in.amazon.mShop.android.shopping", 0xFFFF9900, listOf("amazon.in", "amazon.com", "amzn.in", "amzn.to", "amzn.eu", "a.co")),
        LinkBrand("Flipkart", "com.flipkart.android", 0xFF2874F0, listOf("flipkart.com", "fkrt.it", "fkrt.co", "dl.flipkart.com")),
        LinkBrand("Myntra", "com.myntra.android", 0xFFFF3F6C, listOf("myntra.com", "myntr.it")),
        LinkBrand("Meesho", "com.meesho.supply", 0xFF9F2089, listOf("meesho.com", "msho.in")),
        LinkBrand("Swiggy", "in.swiggy.android", 0xFFFC8019, listOf("swiggy.com", "swiggy.in", "swig.gy")),
        LinkBrand("Zomato", "com.application.zomato", 0xFFE23744, listOf("zomato.com", "zoma.to")),
        LinkBrand("Blinkit", "com.grofers.customerapp", 0xFFF8CB46, listOf("blinkit.com", "blinkit.onelink.me")),
        LinkBrand("Zepto", "com.zeptoconsumerapp", 0xFF5A1E8C, listOf("zeptonow.com", "zepto.co.in", "zep.to")),
        LinkBrand("BigBasket", "com.bigbasket.mobileapp", 0xFF84C225, listOf("bigbasket.com", "bbasket.in")),
        LinkBrand("WhatsApp", "com.whatsapp", 0xFF25D366, listOf("wa.me", "whatsapp.com", "chat.whatsapp.com")),
        LinkBrand("Telegram", "org.telegram.messenger", 0xFF229ED9, listOf("t.me", "telegram.me", "telegram.org")),
        LinkBrand("Instagram", "com.instagram.android", 0xFFE1306C, listOf("instagram.com", "instagr.am")),
        LinkBrand("Facebook", "com.facebook.katana", 0xFF1877F2, listOf("facebook.com", "fb.com", "fb.me", "fb.watch")),
        LinkBrand("X", "com.twitter.android", 0xFF000000, listOf("x.com", "twitter.com", "t.co")),
        LinkBrand("LinkedIn", "com.linkedin.android", 0xFF0A66C2, listOf("linkedin.com", "lnkd.in")),
        LinkBrand("Spotify", "com.spotify.music", 0xFF1DB954, listOf("spotify.com", "spotify.link", "spoti.fi")),
        LinkBrand("Netflix", "com.netflix.mediaclient", 0xFFE50914, listOf("netflix.com")),
        LinkBrand("JioHotstar", "in.startv.hotstar", 0xFF0F1014, listOf("hotstar.com", "jiohotstar.com")),
        LinkBrand("Paytm", "net.one97.paytm", 0xFF00BAF2, listOf("paytm.com", "paytm.me", "p.paytm.me")),
        LinkBrand("PhonePe", "com.phonepe.app", 0xFF5F259F, listOf("phonepe.com", "phon.pe")),
        LinkBrand("Uber", "com.ubercab", 0xFF000000, listOf("uber.com")),
        LinkBrand("Ola", "com.olacabs.customer", 0xFFCDDC39, listOf("olacabs.com", "olacabs.in")),
        LinkBrand("Rapido", "com.rapido.passenger", 0xFFF9C935, listOf("rapido.bike")),
        LinkBrand("IRCTC", "cris.org.in.prs.ima", 0xFF213D77, listOf("irctc.co.in")),
        LinkBrand("Airtel", "com.myairtelapp", 0xFFE40000, listOf("airtel.in", "i.airtel.in")),
        LinkBrand("Jio", "com.jio.myjio", 0xFF0A3A8C, listOf("jio.com")),
        LinkBrand(
            "BSNL", "com.rma.bsnl", 0xFF0072BC,
            listOf("bsnl.co.in", "bsnl.in", "bsnlselfcare.page.link", "mybsnl.page.link"),
        ),
        LinkBrand("HDFC Bank", "com.snapwork.hdfc", 0xFF004C8F, listOf("hdfcbank.com", "hdfc.bank.in", "hdfcbk.io", "hdfcbank.net")),
        LinkBrand("GitHub", "com.github.android", 0xFF24292F, listOf("github.com")),
    )

    /** The brand a URL belongs to, if any. */
    fun match(url: String): LinkBrand? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.rawPath.orEmpty()
        return All.firstOrNull { brand ->
            val hostMatches = brand.hosts.any { host == it || host.endsWith(".$it") }
            hostMatches && (brand.pathPrefixes.isEmpty() || brand.pathPrefixes.any { path.startsWith(it) } ||
                // A brand's own short-link host needs no path ("maps.app.goo.gl/xyz").
                brand.hosts.first { host == it || host.endsWith(".$it") } in DedicatedHosts)
        }
    }

    /** Brand links in a message, one per brand, in reading order, at most [max]. */
    fun find(text: String, max: Int = 2): List<BrandLink> =
        Links.find(text)
            .mapNotNull { link -> match(link.url)?.let { BrandLink(it, link.url) } }
            .distinctBy { it.brand.packageName }
            .take(max)

    /** Hosts used only by a brand that otherwise needs a path match (shared domains). */
    private val DedicatedHosts = setOf("maps.google.com", "maps.app.goo.gl")
}
