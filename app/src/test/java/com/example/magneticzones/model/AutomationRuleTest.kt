package com.example.magneticzones.model

import org.junit.Assert.*
import org.junit.Test

class AutomationRuleTest {
    @Test fun legacyRuleRemainsReadableAndKeepsItsSettingsWhenRenamed() {
        val old = AutomationRule.defaultStoveReminder().copy(enabled = false)
        val renamed = old.withPrompt("Kitchen check", old.description)
        assertEquals(RuleTrigger.AT_LOCATION, renamed.trigger)
        assertEquals("Couch", renamed.locationName)
        assertEquals(old.id, renamed.id)
        assertFalse(renamed.enabled)
        assertEquals(old.description, renamed.description)
    }

    @Test fun freeformEditDoesNotRetainAnUnrelatedStoveTrigger() {
        val edited = AutomationRule.defaultStoveReminder()
            .withPrompt("Water", "When I arrive at my desk, remind me to drink water.")
        assertEquals(RuleTrigger.CUSTOM, edited.trigger)
        assertNull(edited.locationName)
        assertEquals("When I arrive at my desk, remind me to drink water.", edited.description)
        assertEquals("Water", edited.name)
    }
}
