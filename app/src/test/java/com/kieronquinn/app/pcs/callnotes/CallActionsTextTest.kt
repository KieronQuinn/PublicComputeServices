package com.kieronquinn.app.pcs.callnotes

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CallActionsTextTest {

    private val output = """```json
[{"title":"ReminderSmartAction","properties":{"reminder_text":"Check if the money was sent","time":"2026-11-08T10:00"}},
 {"title":"CalendarSmartAction","properties":{"calendar_invite_name":"Aile paketi","description":"Discuss the family plan"}}]
```"""

    @Test
    fun `task texts in order`() {
        val tasks = CallActionsText.parse(output)!!
        assertEquals(listOf("Check if the money was sent", "Aile paketi", "Discuss the family plan"), tasks.texts)
    }

    @Test
    fun `replaced texts keep the task types, other fields and the fence`() {
        val tasks = CallActionsText.parse(output)!!
        val replaced = tasks.replace(listOf("Paranın gönderilip gönderilmediğini kontrol et", "Aile paketi", "Aile paketini konuş"))!!
        assertTrue(replaced.startsWith("```json\n"))
        assertTrue(replaced.endsWith("\n```"))
        val actions = JsonParser.parseString(replaced.substringAfter("```json\n").substringBeforeLast("\n```")) as JsonArray
        val reminder = actions[0].asJsonObject
        assertEquals("ReminderSmartAction", reminder["title"].asString)
        assertEquals("Paranın gönderilip gönderilmediğini kontrol et", reminder["properties"].asJsonObject["reminder_text"].asString)
        assertEquals("2026-11-08T10:00", reminder["properties"].asJsonObject["time"].asString)
        assertEquals("Aile paketini konuş", actions[1].asJsonObject["properties"].asJsonObject["description"].asString)
    }

    @Test
    fun `unchanged texts give no new output`() {
        val tasks = CallActionsText.parse(output)!!
        assertNull(tasks.replace(tasks.texts))
    }

    @Test
    fun `blank replacement keeps the original`() {
        val tasks = CallActionsText.parse(output)!!
        assertNull(tasks.replace(listOf(" ", "Aile paketi", "Discuss the family plan")))
    }

    @Test
    fun `wrong number of replacements is refused`() {
        val tasks = CallActionsText.parse(output)!!
        assertThrows(IllegalArgumentException::class.java) { tasks.replace(listOf("a")) }
    }

    @Test
    fun `output without task text is left alone`() {
        assertNull(CallActionsText.parse("[]"))
        assertNull(CallActionsText.parse("""[{"title":"ReminderSmartAction","properties":{"reminder_text":" "}}]"""))
        assertNull(CallActionsText.parse("No tasks"))
        assertNull(CallActionsText.parse("[not json"))
        assertNull(CallActionsText.parse("""[{"title":"X","properties":{"reminder_text":5}}]"""))
    }

}
