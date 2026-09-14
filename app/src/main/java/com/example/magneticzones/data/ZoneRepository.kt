package com.example.magneticzones.data

import com.example.magneticzones.model.ExperimentSession
import com.example.magneticzones.model.AutomationRule
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone
import kotlinx.coroutines.flow.StateFlow

interface ZoneRepository {
    val zones: StateFlow<List<Zone>>
    val trainingSessions: StateFlow<List<TrainingSession>>
    val experimentSessions: StateFlow<List<ExperimentSession>>
    val automationRules: StateFlow<List<AutomationRule>>
    suspend fun addZone(name: String): Zone
    suspend fun deleteZone(zoneId: String)
    suspend fun saveTrainingSession(session: TrainingSession)
    suspend fun saveExperimentSession(session: ExperimentSession)
    suspend fun saveAutomationRule(rule: AutomationRule)
    suspend fun deleteAutomationRule(ruleId: String)
}
