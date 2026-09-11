package com.csyncvibe.app

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

    private class ContactBuilder(
        val id: String,
        val displayName: String
    ) {
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()

        fun build() = Contact(id, displayName, phones.toList(), emails.toList())
    }
}
