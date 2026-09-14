package com.example.magneticzones.model

import org.junit.Assert.*
import org.junit.Test

class PromptMentionsTest {
    private val room = PromptVariable("Dining Table", PromptReferenceKind.ROOM, "room-42", "Saved room")

    @Test fun detectsTheQueryAtTheCursorInsteadOfTheEnd() {
        assertEquals(MentionQuery(5, 9, "Din"), PromptMentions.query("When @Din then notify me", 9, 9, emptyList()))
        assertNull(PromptMentions.query("me@example.com", 5, 5, emptyList()))
        assertNull(PromptMentions.query("@Dining", 0, 7, emptyList()))
        assertNull(PromptMentions.query("", 0, 0, emptyList()))
    }

    @Test fun insertionPreservesFollowingTextAndDoesNotReopenThePicker() {
        val input = "When @Din then notify me"
        val result = PromptMentions.insert(input, MentionQuery(5, 9, "Din"), room, emptyList())!!
        assertEquals("When @Dining Table then notify me", result.text)
        assertEquals("room-42", result.references.single().targetId)
        assertNull(PromptMentions.query(result.text, result.cursor + 1, result.cursor + 1, result.references))
    }

    @Test fun appendingAndInsertingBeforeAMentionPreservesItsId() {
        val old = "@Dining Table"
        val reference = PromptReference(0, room.label, room.kind, room.targetId)
        val shifted = PromptMentions.afterEdit(old, "At $old", listOf(reference))
        assertEquals(reference.copy(start = 3), shifted.single())
        assertEquals(listOf(reference), PromptMentions.afterEdit(old, "$old now", listOf(reference)))
    }

    @Test fun changingOneMentionUnlinksItAndKeepsOtherMentions() {
        val refs = listOf(PromptReference(0, "Couch", PromptReferenceKind.ROOM, "couch"), PromptReference(11, "Stove state", PromptReferenceKind.VALUE, "stove.state"))
        val result = PromptMentions.afterEdit("@Couch and @Stove state", "@Desk and @Stove state", refs)
        assertEquals(listOf(refs[1].copy(start = 10)), result)
    }

    @Test fun deletingAnIdenticallyNamedRoomKeepsTheOtherRoomsId() {
        val refs = listOf(PromptReference(0, "Desk", PromptReferenceKind.ROOM, "first"), PromptReference(6, "Desk", PromptReferenceKind.ROOM, "second"))
        val result = PromptMentions.afterEdit("@Desk @Desk", "@Desk", refs, 0, 6)
        assertEquals("second", result.single().targetId)
        assertEquals(0, result.single().start)
    }

    @Test fun savingTrimsWhitespaceAndRebasesReferences() {
        val reference = PromptReference(2, room.label, room.kind, room.targetId)
        val saved = AutomationRule.defaultStoveReminder().withPrompt("Room check", "  @Dining Table  ", listOf(reference))
        assertEquals("@Dining Table", saved.prompt)
        assertEquals(reference.copy(start = 0), saved.promptReferences.single())
        assertEquals(saved.promptReferences, saved.withPrompt("Renamed", saved.description).promptReferences)
    }

    @Test fun duplicateRoomNamesRemainDistinctAndRoomsAreNotInvented() {
        val choices = PromptMentions.variables(listOf(Zone("a", "Desk", 0), Zone("b", "Desk", 0)))
        assertEquals(listOf("a", "b"), choices.filter { it.kind == PromptReferenceKind.ROOM }.map { it.targetId })
        assertNotEquals(choices[0].detail, choices[1].detail)
        assertTrue(PromptMentions.variables(emptyList()).none { it.kind == PromptReferenceKind.ROOM })
    }

    @Test fun insertionRespectsTheLengthLimitAndLeavesPunctuationIntact() {
        assertNull(PromptMentions.insert("x".repeat(1998) + " @", MentionQuery(1999, 2000, ""), room, emptyList()))
        val result = PromptMentions.insert("At @Din, notify me", MentionQuery(3, 7, "Din"), room, emptyList())!!
        assertEquals("At @Dining Table, notify me", result.text)
    }
}
