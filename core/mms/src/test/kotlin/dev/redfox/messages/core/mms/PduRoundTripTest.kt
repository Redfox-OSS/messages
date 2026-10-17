package dev.redfox.messages.core.mms

import android.content.ContextWrapper
import dev.redfox.messages.core.mms.pdu.CharacterSets
import dev.redfox.messages.core.mms.pdu.EncodedStringValue
import dev.redfox.messages.core.mms.pdu.NotifyRespInd
import dev.redfox.messages.core.mms.pdu.PduBody
import dev.redfox.messages.core.mms.pdu.PduComposer
import dev.redfox.messages.core.mms.pdu.PduHeaders
import dev.redfox.messages.core.mms.pdu.PduParser
import dev.redfox.messages.core.mms.pdu.PduPart
import dev.redfox.messages.core.mms.pdu.SendReq
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vendored AOSP composer and parser agree with each other on what the app sends. */
class PduRoundTripTest {

    /** The composer only uses its context to read parts given by URI; these tests use bytes. */
    private val context = ContextWrapper(null)

    private fun part(type: String, name: String, data: ByteArray, text: Boolean = false) = PduPart().apply {
        contentType = type.toByteArray()
        this.name = name.toByteArray()
        contentId = "<$name>".toByteArray()
        contentLocation = name.toByteArray()
        if (text) charset = CharacterSets.UTF_8
        this.data = data
    }

    @Test
    fun `send request with text and image survives compose then parse`() {
        val image = ByteArray(2048) { (it % 251).toByte() }
        val request = SendReq().apply {
            addTo(EncodedStringValue("+919876543210/TYPE=PLMN"))
            addTo(EncodedStringValue("+919812345678/TYPE=PLMN"))
            date = 1_792_000_000L
            setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
            body = PduBody().apply {
                addPart(part("text/plain", "text_0.txt", "Hello from the group 👋".toByteArray(), text = true))
                addPart(part("image/jpeg", "image_0.jpg", image))
            }
        }
        val bytes = PduComposer(context, request).make()
        val parsed = PduParser(bytes).parse() as SendReq

        // The parser drops the "/TYPE=PLMN" routing suffix the composer sends.
        assertEquals(listOf("+919876543210", "+919812345678"), parsed.to.map { it.string })
        assertEquals(2, parsed.body.partsNum)
        assertEquals("Hello from the group 👋", String(parsed.body.getPart(0).data, Charsets.UTF_8))
        assertEquals("image/jpeg", String(parsed.body.getPart(1).contentType))
        assertArrayEquals(image, parsed.body.getPart(1).data)
    }

    @Test
    fun `plain numbers get one routing suffix each, and the sender's number goes as From`() {
        val request = SendReq().apply {
            from = EncodedStringValue("+916350000000")
            addTo(EncodedStringValue("+919876543210"))
            body = PduBody().apply { addPart(part("text/plain", "text_0.txt", "Hi".toByteArray(), text = true)) }
        }
        val bytes = PduComposer(context, request).make()
        val wire = String(bytes, Charsets.ISO_8859_1)

        assertEquals(2, Regex(Regex.escape("/TYPE=PLMN")).findAll(wire).count()) // From and To, once each
        assertTrue(wire.contains("+919876543210/TYPE=PLMN"))
        assertEquals("+916350000000", (PduParser(bytes).parse() as SendReq).from.string)
    }

    @Test
    fun `download acknowledgement encodes as notify-resp`() {
        val ack = NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, "T123".toByteArray(), PduHeaders.STATUS_RETRIEVED)
        val bytes = PduComposer(context, ack).make()
        assertTrue(bytes.isNotEmpty())
        val parsed = PduParser(bytes).parse() as NotifyRespInd
        assertEquals("T123", String(parsed.transactionId))
        assertEquals(PduHeaders.STATUS_RETRIEVED, parsed.status)
    }
}
