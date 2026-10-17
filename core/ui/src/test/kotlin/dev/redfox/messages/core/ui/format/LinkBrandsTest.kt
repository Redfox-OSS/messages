package dev.redfox.messages.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinkBrandsTest {

    private fun brand(url: String) = LinkBrands.match(url)?.name

    @Test
    fun `play store and youtube`() {
        assertEquals("Play Store", brand("https://play.google.com/store/apps/details?id=com.bsnl.app"))
        assertEquals("YouTube", brand("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("YouTube", brand("https://m.youtube.com/watch?v=abc"))
    }

    @Test
    fun `short links map to their brand`() {
        assertEquals("Amazon", brand("https://amzn.in/d/3xYz"))
        assertEquals("Flipkart", brand("https://fkrt.it/abc"))
        assertEquals("WhatsApp", brand("https://wa.me/919876543210"))
    }

    @Test
    fun `google maps needs a maps path on shared google hosts`() {
        assertEquals("Google Maps", brand("https://www.google.com/maps/place/Kolkata"))
        assertEquals("Google Maps", brand("https://maps.app.goo.gl/AbCdEf"))
        assertNull(brand("https://www.google.com/search?q=sms"))
        assertNull(brand("https://goo.gl/abc"))
    }

    @Test
    fun `lookalike hosts do not match`() {
        assertNull(brand("https://notyoutube.com/watch"))
        assertNull(brand("https://youtube.com.evil.example/x"))
        assertNull(brand("https://hdfcbank.com.example.net/x"))
    }

    @Test
    fun `bsnl and hdfc bank links, including their short links`() {
        assertEquals("BSNL", brand("https://bsnlapp.bsnl.co.in/bsnl/app"))
        assertEquals("BSNL", brand("https://bsnlselfcare.page.link/abc"))
        assertEquals("BSNL", brand("https://bookmyfiber.bsnl.co.in/x"))
        assertEquals("HDFC Bank", brand("https://hdfcbk.io/HDFCBK/a/xyz"))
        assertEquals("HDFC Bank", brand("https://1.hdfc.bank.in/x"))
        assertEquals("HDFC Bank", brand("https://www.hdfcbank.com/personal"))
        // Other banks on the shared .bank.in domain aren't HDFC.
        assertNull(brand("https://ccm.axis.bank.in/AXISBK/t3CrXKnx"))
    }

    @Test
    fun `scheme-less short links found in sms text get a button`() =
        assertEquals(
            listOf("HDFC Bank", "BSNL"),
            LinkBrands.find("Pay at hdfcbk.io/HDFCBK/a/1 or recharge via bsnlselfcare.page.link/xy").map { it.brand.name },
        )

    @Test
    fun `find returns one button per brand in order`() {
        val text = "Watch https://youtu.be/a and https://youtube.com/b, then rate us at " +
            "https://play.google.com/store/apps/details?id=x"
        assertEquals(listOf("YouTube", "Play Store"), LinkBrands.find(text).map { it.brand.name })
        assertEquals("https://youtu.be/a", LinkBrands.find(text).first().url)
    }

    @Test
    fun `every brand app is declared in the manifest queries`() {
        // Android 11+ hides other apps unless declared; a missing <package> means no icon.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val missing = LinkBrands.All.map { it.packageName }.filterNot { "\"$it\"" in manifest }
        assertTrue("Missing from <queries>: $missing", missing.isEmpty())
    }
}
