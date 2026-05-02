package com.pingra.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.pingra.app.data.SmsRepository

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val repository = SmsRepository(context)
        messages
            .groupBy { message ->
                message.displayOriginatingAddress
                    ?: message.originatingAddress
                    ?: ""
            }
            .forEach { (address, parts) ->
                val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
                val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                repository.insertIncomingMessage(address, body, timestamp)
            }

        resultCode = Activity.RESULT_OK
    }
}

