package com.example.magneticzones.automation

import com.example.magneticzones.model.AutomationRule
import com.example.magneticzones.model.PromptReferenceKind
import com.example.magneticzones.model.RuleTrigger
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone

data class ExecutableRule(val rule: AutomationRule, val roomId: String, val roomName: String)

sealed interface RuleCompilation {
    data class Ready(val value: ExecutableRule) : RuleCompilation
    data class Incomplete(val message: String) : RuleCompilation
}

object RuleCompiler {
    fun compile(rule: AutomationRule, zones: List<Zone>, training: List<TrainingSession>): RuleCompilation {
        val roomId = when {
            rule.trigger == RuleTrigger.AT_LOCATION -> zones.firstOrNull {
                it.name.equals(rule.locationName, ignoreCase = true)
            }?.id
            else -> rule.promptReferences.singleOrNull { it.kind == PromptReferenceKind.ROOM && it.matches(rule.prompt) }?.targetId
        } ?: return RuleCompilation.Incomplete("Link one room with @")

        val zone = zones.firstOrNull { it.id == roomId }
            ?: return RuleCompilation.Incomplete("Linked room was removed")
        if (training.none { it.zoneId == roomId && it.windows.isNotEmpty() }) {
            return RuleCompilation.Incomplete("Train ${zone.name} in Settings")
        }

        val usesStove = rule.trigger != RuleTrigger.CUSTOM ||
            rule.promptReferences.any { it.kind == PromptReferenceKind.VALUE && it.targetId == "stove.state" && it.matches(rule.prompt) }
        if (!usesStove) return RuleCompilation.Incomplete("Link @Stove state")
        if (rule.trigger == RuleTrigger.CUSTOM && !Regex("\\bon\\b", RegexOption.IGNORE_CASE).containsMatchIn(rule.prompt)) {
            return RuleCompilation.Incomplete("Say when the stove is on")
        }
        return RuleCompilation.Ready(ExecutableRule(rule, roomId, zone.name))
    }
}

enum class TrackerConnection { CHECKING, CONNECTED, OFFLINE }

data class RuleMonitorSnapshot(
    val running: Boolean = false,
    val trackerConnection: TrackerConnection = TrackerConnection.CHECKING,
    val stoveState: String = "unknown",
    val activeZoneId: String? = null,
    val notificationsAllowed: Boolean = true,
    val lastError: String? = null,
)
