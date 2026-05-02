package com.pingra.app.data

const val UNKNOWN_SENDER = "Unknown sender"

data class SmsThread(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val timestampMillis: Long,
    val messageCount: Int,
)

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestampMillis: Long,
    val direction: SmsDirection,
)

enum class SmsDirection {
    Incoming,
    Outgoing,
}

data class SmsRow(
    val id: Long,
    val threadId: Long,
    val address: String?,
    val body: String?,
    val timestampMillis: Long,
    val type: Int,
)

