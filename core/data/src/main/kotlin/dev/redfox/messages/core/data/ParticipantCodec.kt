package dev.redfox.messages.core.data

import dev.redfox.messages.core.model.Contact

/**
 * Packs participants into one column. ASCII unit/record separators can't appear in phone
 * numbers, names or content URIs, so no escaping is needed.
 */
internal object ParticipantCodec {
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(contacts: List<Contact>): String =
        contacts.joinToString(RECORD.toString()) { c ->
            listOf(c.address, c.name.orEmpty(), c.photoUri.orEmpty(), c.formattedAddress).joinToString(FIELD.toString())
        }

    fun decode(value: String): List<Contact> =
        if (value.isEmpty()) emptyList()
        else value.split(RECORD).map { record ->
            val f = record.split(FIELD)
            Contact(
                address = f[0],
                name = f.getOrNull(1)?.ifEmpty { null },
                photoUri = f.getOrNull(2)?.ifEmpty { null },
                formattedAddress = f.getOrNull(3)?.ifEmpty { null } ?: f[0],
            )
        }
}
