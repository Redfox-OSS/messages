package dev.redfox.messages.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.model.Contact
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns message addresses into [Contact]s: name and photo from the phone's contacts, and a
 * nicely formatted number otherwise. Results are cached and dropped whenever contacts change.
 * Blocking; call from an IO dispatcher.
 */
@Singleton
class ContactResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Address → (contact, whether it was looked up with contacts permission). */
    private val cache = ConcurrentHashMap<String, Pair<Contact, Boolean>>()

    private val countryIso: String by lazy {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        (telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country).uppercase(Locale.ROOT)
    }

    /** Drops cached names whenever the phone book changes. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = cache.clear()
    }

    @Volatile private var observing = false

    /**
     * Listening to the contacts provider needs READ_CONTACTS: Samsung's provider throws
     * without it (a fresh install crashed on launch). So register only once permission is
     * there, the first time a lookup runs with it.
     */
    private fun observeContactsIfPermitted(permitted: Boolean) {
        if (observing || !permitted) return
        synchronized(this) {
            if (observing) return
            observing = try {
                context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }

    fun resolve(address: String): Contact {
        val permitted = hasContactsPermission()
        observeContactsIfPermitted(permitted)
        cache[address]?.let { (contact, lookedUpWithPermission) ->
            // Re-resolve once if contacts access was granted after the first lookup.
            if (lookedUpWithPermission || !permitted) return contact
        }
        return lookup(address, permitted).also { cache[address] = it to permitted }
    }

    fun resolveAll(addresses: Collection<String>): Map<String, Contact> =
        addresses.toSet().associateWith(::resolve)

    private fun lookup(address: String, permitted: Boolean): Contact {
        val base = Contact(address = address, formattedAddress = format(address))
        // Bank / service senders ("VM-HDFCBK") are never in contacts.
        if (base.isAlphanumericSender || !permitted) return base

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use base
            base.copy(
                name = c.getString(0)?.takeIf { it.isNotBlank() },
                photoUri = c.getString(1),
            )
        } ?: base
    }

    private fun format(address: String): String = when {
        address.startsWith("sip:", ignoreCase = true) -> businessChatName(address)
        address.any { it.isLetter() } -> address
        else -> PhoneNumberUtils.formatNumber(address, countryIso) ?: address
    }

    /**
     * RCS Business Messaging agents are addressed like `sip:swish_e0vhfudq_agent@rbm.goog`.
     * The brand is the first segment of the local part: "Swish".
     */
    private fun businessChatName(address: String): String {
        val local = address.substringAfter(':').substringBefore('@')
        val brand = local.removeSuffix("_agent").substringBeforeLast('_', local.removeSuffix("_agent"))
            .replace('_', ' ').replace('-', ' ').trim()
        return brand.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
            .ifBlank { address }
    }

    private fun hasContactsPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
}
