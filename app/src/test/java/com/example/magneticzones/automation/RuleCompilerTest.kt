package com.example.magneticzones.automation

import com.example.magneticzones.model.*
import org.junit.Assert.*
import org.junit.Test

class RuleCompilerTest {
    private val room = Zone("dining", "Dining Table", 0)
    private val training = listOf(TrainingSession("visit", room.id, 0, 1, emptyList(), listOf(window())))

    @Test fun linkedRoomAndStoveStateCompileToAnExecutableRule() {
        val prompt = "When I'm at @Dining Table, remind me if @Stove state is on."
        val rule = AutomationRule(
            "rule", "Dinner check", RuleTrigger.CUSTOM, null, true, 0, prompt,
            listOf(
                PromptReference(prompt.indexOf("@Dining Table"), "Dining Table", PromptReferenceKind.ROOM, room.id),
                PromptReference(prompt.indexOf("@Stove state"), "Stove state", PromptReferenceKind.VALUE, "stove.state"),
            ),
        )
        val result = RuleCompiler.compile(rule, listOf(room), training) as RuleCompilation.Ready
        assertEquals(room.id, result.value.roomId)
    }

    @Test fun missingTrainingOrStoveReferenceCannotAppearActive() {
        val roomOnly = AutomationRule("rule", "Rule", RuleTrigger.CUSTOM, null, true, 0, "At @Dining Table when on", listOf(
            PromptReference(3, "Dining Table", PromptReferenceKind.ROOM, room.id)
        ))
        assertEquals("Train Dining Table in Settings", (RuleCompiler.compile(roomOnly, listOf(room), emptyList()) as RuleCompilation.Incomplete).message)
        assertEquals("Link @Stove state", (RuleCompiler.compile(roomOnly, listOf(room), training) as RuleCompilation.Incomplete).message)
    }

    @Test fun legacyStoveRuleStillCompilesByRoomName() {
        val result = RuleCompiler.compile(AutomationRule.defaultStoveReminder().copy(locationName = room.name), listOf(room), training)
        assertTrue(result is RuleCompilation.Ready)
    }

    private fun window() = FeatureWindow(0, 1, 4, emptyMap())
}
