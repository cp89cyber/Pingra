package com.pingra.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.telephony.SmsManager
import com.pingra.app.sms.SmsRoleHelper

class SmsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    fun loadThreads(): List<SmsThread> {
        val rows = querySmsRows(
            selection = null,
            selectionArgs = null,
            sortOrder = "${Telephony.Sms.DATE} DESC",
        )
        return SmsThreadMapper.toThreads(rows)
    }

    fun loadMessages(threadId: Long): List<SmsMessage> {
        if (threadId < 0) return emptyList()

        val rows = querySmsRows(
            selection = "${Telephony.Sms.THREAD_ID} = ?",
            selectionArgs = arrayOf(threadId.toString()),
            sortOrder = "${Telephony.Sms.DATE} ASC",
        )
        return SmsThreadMapper.toMessages(rows)
    }

    @Suppress("DEPRECATION")
    fun sendMessage(destinationAddress: String, body: String) {
        val trimmedAddress = destinationAddress.trim()
        val trimmedBody = body.trim()
        require(trimmedAddress.isNotBlank()) { "Destination address is required." }
        require(trimmedBody.isNotBlank()) { "Message body is required." }

        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(trimmedBody)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(trimmedAddress, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(trimmedAddress, null, trimmedBody, null, null)
        }

        if (SmsRoleHelper(appContext).isDefaultSmsApp()) {
            insertSentMessage(trimmedAddress, trimmedBody, System.currentTimeMillis())
        }
    }

    fun insertIncomingMessage(address: String, body: String, timestampMillis: Long) {
        if (!SmsRoleHelper(appContext).isDefaultSmsApp()) return

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    private fun insertSentMessage(address: String, body: String, timestampMillis: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    private fun querySmsRows(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String,
    ): List<SmsRow> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )

        return contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    add(
                        SmsRow(
                            id = cursor.getLong(idIndex),
                            threadId = cursor.getLong(threadIdIndex),
                            address = cursor.getNullableString(addressIndex),
                            body = cursor.getNullableString(bodyIndex),
                            timestampMillis = cursor.getLong(dateIndex),
                            type = cursor.getInt(typeIndex),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun Cursor.getNullableString(index: Int): String? {
        return if (isNull(index)) null else getString(index)
    }
}
