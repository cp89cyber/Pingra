package com.pingra.app.sms

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.pingra.app.data.SmsRepository

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart
            ?.substringBefore("?")
            ?.let(Uri::decode)
            ?.trim()
            .orEmpty()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra("sms_body")
            ?: ""

        if (address.isNotBlank() && body.isNotBlank()) {
            runCatching {
                SmsRepository(this).sendMessage(address, body)
            }
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }
}
