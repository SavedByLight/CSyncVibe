package com.csyncvibe.app

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.google.gson.annotations.SerializedName

data class Contact(
    @SerializedName("id") val id: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("phones") val phones: List<String>,
    @SerializedName("emails") val emails: List<String>
)

class ContactRepository(private val context: Context) {

    fun getAllContacts(): List<Contact> {
        val contacts = mutableMapOf<String, ContactBuilder>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // Phones
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: ""
                val number = cursor.getString(numberIdx)?.trim() ?: continue

                val builder = contacts.getOrPut(id) { ContactBuilder(id, name) }
                if (number.isNotEmpty() && !builder.phones.contains(number)) {
                    builder.phones.add(number)
                }
            }
        }

        // Emails
        val emailProjection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            emailProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val email = cursor.getString(emailIdx)?.trim() ?: continue

                val builder = contacts[id] ?: continue
                if (email.isNotEmpty() && !builder.emails.contains(email)) {
                    builder.emails.add(email)
                }
            }
        }

        return contacts.values
            .map { it.build() }
            .filter { it.displayName.isNotBlank() || it.phones.isNotEmpty() || it.emails.isNotEmpty() }
            .sortedBy { it.displayName.lowercase() }
    }

    /**
     * Import contacts from GitHub into the device.
     * Skips contacts that already exist (matched by display name + at least one phone/email).
     * Returns the number of contacts newly inserted.
     */
    fun importContacts(remoteContacts: List<Contact>): Int {
        if (remoteContacts.isEmpty()) return 0

        val existing = getAllContacts()
        val existingKeys = existing.map { contactKey(it) }.toSet()

        var inserted = 0

        for (contact in remoteContacts) {
            val key = contactKey(contact)
            if (key in existingKeys) continue
            if (contact.displayName.isBlank() && contact.phones.isEmpty() && contact.emails.isEmpty()) continue

            val ops = ArrayList<ContentProviderOperation>()

            // Create raw contact
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Display name
            if (contact.displayName.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            contact.displayName
                        )
                        .build()
                )
            }

            // Phones
            for (phone in contact.phones) {
                if (phone.isBlank()) continue
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }

            // Emails
            for (email in contact.emails) {
                if (email.isBlank()) continue
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                        )
                        .build()
                )
            }

            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                inserted++
            } catch (e: Exception) {
                // Skip this contact and continue
            }
        }

        return inserted
    }

    private fun contactKey(c: Contact): String {
        val name = c.displayName.trim().lowercase()
        val phones = c.phones.map { it.replace("\\s".toRegex(), "") }.sorted().joinToString("|")
        val emails = c.emails.map { it.trim().lowercase() }.sorted().joinToString("|")
        return "$name::$phones::$emails"
    }

    private class ContactBuilder(
        val id: String,
        val displayName: String
    ) {
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()

        fun build() = Contact(id, displayName, phones.toList(), emails.toList())
    }
}
