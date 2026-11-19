package com.sameerasw.essentials.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherAssistantCommandParsingTest {

    @Test
    fun timerDurationsParseNaturalFormats() {
        assertEquals(300L, LauncherAssistantCommands.parseDurationSeconds("5"))
        assertEquals(300L, LauncherAssistantCommands.parseDurationSeconds("5 minutes"))
        assertEquals(5400L, LauncherAssistantCommands.parseDurationSeconds("1h30m"))
        assertEquals(5400L, LauncherAssistantCommands.parseDurationSeconds("1 hour 30 minutes"))
        assertEquals(90L, LauncherAssistantCommands.parseDurationSeconds("1:30"))
        assertEquals(45L, LauncherAssistantCommands.parseDurationSeconds("45s"))
    }

    @Test
    fun clockCommandsAcceptNaturalPhrasings() {
        assertEquals("5", LauncherAssistantCommands.timerDurationFor("timer 5"))
        assertEquals("5", LauncherAssistantCommands.timerDurationFor("timer for 5"))
        assertEquals("5", LauncherAssistantCommands.timerDurationFor("set a timer for 5"))
        assertEquals("5", LauncherAssistantCommands.timerDurationFor("start a timer 5"))
        assertEquals("5 minute", LauncherAssistantCommands.timerDurationFor("start a 5 minute timer"))
        assertEquals("5", LauncherAssistantCommands.timerDurationFor("countdown 5"))
        assertEquals("", LauncherAssistantCommands.timerDurationFor("start a timer"))
        assertNull(LauncherAssistantCommands.timerDurationFor("show timers"))

        assertEquals("7", LauncherAssistantCommands.alarmTimeFor("wake me at 7"))
        assertEquals("7", LauncherAssistantCommands.alarmTimeFor("wake me up at 7"))
        assertEquals("30 minutes", LauncherAssistantCommands.alarmTimeFor("alarm in 30 minutes"))
        assertEquals("7:30pm", LauncherAssistantCommands.alarmTimeFor("set an alarm for 7:30pm"))
        assertEquals("", LauncherAssistantCommands.alarmTimeFor("alarm"))
        assertNull(LauncherAssistantCommands.alarmTimeFor("open alarms"))
    }

    @Test
    fun messageCommandsParseRecipientAndBody() {
        val sms = LauncherAssistantCommands.parseMessageRequest(
            "send a text to John saying running late",
            emptyList(),
        )
        assertNotNull(sms)
        assertEquals("John", sms!!.name)
        assertEquals("running late", sms.body)
        assertEquals(LauncherAssistantCommands.MessageChannel.SMS, sms.channel)

        val recipientFirst = LauncherAssistantCommands.parseMessageRequest(
            "send John a text saying on my way",
            emptyList(),
        )
        assertEquals("John", recipientFirst!!.name)
        assertEquals("on my way", recipientFirst.body)

        val whatsapp = LauncherAssistantCommands.parseMessageRequest(
            "send a whatsapp message to John saying hello",
            emptyList(),
        )
        assertEquals("John", whatsapp!!.name)
        assertEquals("hello", whatsapp.body)
        assertEquals(LauncherAssistantCommands.MessageChannel.WHATSAPP, whatsapp.channel)

        val whatsappSuffix = LauncherAssistantCommands.parseMessageRequest(
            "message John on WhatsApp saying hi",
            emptyList(),
        )
        assertEquals("John", whatsappSuffix!!.name)
        assertEquals("hi", whatsappSuffix.body)
        assertEquals(LauncherAssistantCommands.MessageChannel.WHATSAPP, whatsappSuffix.channel)
    }

    @Test
    fun whatsappWithoutSeparatorSplitsFirstWord() {
        val request = LauncherAssistantCommands.parseMessageRequest("whatsapp John hello", emptyList())
        assertNotNull(request)
        assertEquals("John", request!!.name)
        assertEquals("hello", request.body)
        assertEquals(LauncherAssistantCommands.MessageChannel.WHATSAPP, request.channel)
    }
}
