package com.pingra.app.data

object SmsThreadMapper {
    private const val MESSAGE_TYPE_INBOX = 1
    private const val MESSAGE_TYPE_SENT = 2
    private const val MESSAGE_TYPE_OUTBOX = 4
    private const val MESSAGE_TYPE_FAILED = 5
    private const val MESSAGE_TYPE_QUEUED = 6

    private val outgoingTypes = setOf(
        MESSAGE_TYPE_SENT,
        MESSAGE_TYPE_OUTBOX,
        MESSAGE_TYPE_FAILED,
        MESSAGE_TYPE_QUEUED,
    )

    fun toThreads(rows: List<SmsRow>): List<SmsThread> {
        return rows
            .groupBy { it.threadId }
            .map { (threadId, threadRows) ->
                val newest = threadRows.maxBy { it.timestampMillis }
                val address = threadRows
                    .firstNotNullOfOrNull { row -> row.address?.trim()?.takeIf { it.isNotBlank() } }
                    ?: newest.address
                SmsThread(
                    threadId = threadId,
                    address = address.displayAddress(),
                    snippet = newest.body.orEmpty(),
                    timestampMillis = newest.timestampMillis,
                    messageCount = threadRows.size,
                )
            }
            .sortedByDescending { it.timestampMillis }
    }

    fun toMessages(rows: List<SmsRow>): List<SmsMessage> {
        return rows
            .sortedWith(compareBy<SmsRow> { it.timestampMillis }.thenBy { it.id })
            .map { row ->
                SmsMessage(
                    id = row.id,
                    threadId = row.threadId,
                    address = row.address.displayAddress(),
                    body = row.body.orEmpty(),
                    timestampMillis = row.timestampMillis,
                    direction = if (row.type in outgoingTypes) SmsDirection.Outgoing else SmsDirection.Incoming,
                )
            }
    }

    private fun String?.displayAddress(): String {
        val trimmed = this?.trim().orEmpty()
        return trimmed.ifBlank { UNKNOWN_SENDER }
    }
}
