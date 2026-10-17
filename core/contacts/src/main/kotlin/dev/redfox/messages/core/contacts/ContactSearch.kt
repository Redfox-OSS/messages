package dev.redfox.messages.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.model.Contact
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-book search for the recipient picker: one result per phone number, names A–Z.
 * Matches names and numbers (the provider's filter does both). Empty without READ_CONTACTS.
 * Blocking; call from an IO dispatcher.
 */
@Singleton
class ContactSearch @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun search(query: String, limit: Int): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val uri = if (query.isBlank()) Phone.CONTENT_URI
        else Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val seen = HashSet<String>()
        val out = ArrayList<Contact>()
        context.contentResolver.query(
            uri,
            arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.NORMALIZED_NUMBER, Phone.PHOTO_THUMBNAIL_URI),
            "${Phone.NUMBER} IS NOT NULL",
            null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (out.size < limit && c.moveToNext()) {
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                // The same number is often saved twice (WhatsApp, SIM, Google); show it once.
                val key = c.getString(2) ?: number.filter { it.isDigit() || it == '+' }
                if (!seen.add(key.takeLast(10))) continue
                out += Contact(
                    address = number,
                    name = c.getString(0)?.takeIf { it.isNotBlank() },
                    photoUri = c.getString(3),
                    formattedAddress = number,
                )
            }
        }
        return out
    }
}
