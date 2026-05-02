package com.pingra.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsThreadMapperTest {
    @Test
    fun toThreads_groupsRowsByThreadAndUsesNewestSnippet() {
        val rows = listOf(
            SmsRow(
                id = 1,
                threadId = 10,
                address = "+15550001",
                body = "older",
                timestampMillis = 100,
                type = 1,
            ),
            SmsRow(
                id = 2,
                threadId = 10,
                address = "+15550001",
                body = "newer",
                timestampMillis = 200,
                type = 2,
            ),
            SmsRow(
                id = 3,
                threadId = 20,
                address = "+15550002",
                body = "other",
                timestampMillis = 150,
                type = 1,
            ),
        )

        val threads = SmsThreadMapper.toThreads(rows)

        assertEquals(listOf(10L, 20L), threads.map { it.threadId })
        assertEquals("newer", threads.first().snippet)
        assertEquals(2, threads.first().messageCount)
    }

    @Test
    fun toMessages_ordersByDateAndMapsOutgoingTypes() {
        val rows = listOf(
            SmsRow(
                id = 2,
                threadId = 10,
                address = "+15550001",
                body = "sent",
                timestampMillis = 200,
                type = 2,
            ),
            SmsRow(
                id = 1,
                threadId = 10,
                address = "+15550001",
                body = "received",
                timestampMillis = 100,
                type = 1,
            ),
        )

        val messages = SmsThreadMapper.toMessages(rows)

        assertEquals(listOf("received", "sent"), messages.map { it.body })
        assertEquals(SmsDirection.Incoming, messages[0].direction)
        assertEquals(SmsDirection.Outgoing, messages[1].direction)
    }

    @Test
    fun toThreads_usesUnknownSenderForBlankAddresses() {
        val threads = SmsThreadMapper.toThreads(
            listOf(
                SmsRow(
                    id = 1,
                    threadId = 10,
                    address = " ",
                    body = "hello",
                    timestampMillis = 100,
                    type = 1,
                ),
            ),
        )

        assertEquals(UNKNOWN_SENDER, threads.single().address)
    }
}

