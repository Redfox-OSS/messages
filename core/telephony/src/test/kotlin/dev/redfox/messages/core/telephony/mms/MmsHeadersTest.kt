package dev.redfox.messages.core.telephony.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsHeadersTest {

    @Test
    fun `a Samsung phone with an empty carrier config sends Samsung's headers`() {
        val headers = MmsHeaders.overrides("", null, manufacturer = "samsung", model = "SM-S911B")
        assertEquals("SAMSUNG-ANDROID-MMS/SM-S911B", headers.userAgent)
        assertEquals("http://wap.samsungmobile.com/uaprof/SM-S911B.xml", headers.uaProfUrl)
    }

    @Test
    fun `other makers get AOSP's headers`() {
        val headers = MmsHeaders.overrides(null, null, manufacturer = "vivo", model = "V2309")
        assertEquals(MmsHeaders.AOSP_USER_AGENT, headers.userAgent)
        assertEquals(MmsHeaders.AOSP_UA_PROF_URL, headers.uaProfUrl)
    }

    @Test
    fun `headers the carrier config sets are left alone`() {
        val headers = MmsHeaders.overrides("CarrierUA/1.0", "http://carrier.example/ua.xml", manufacturer = "samsung", model = "SM-S911B")
        assertNull(headers.userAgent)
        assertNull(headers.uaProfUrl)
    }
}
