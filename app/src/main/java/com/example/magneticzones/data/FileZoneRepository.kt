package com.example.magneticzones.data

import android.content.Context
import com.example.magneticzones.model.ExperimentSession
import com.example.magneticzones.model.AutomationRule
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileZoneRepository(context: Context) : ZoneRepository {
    private val file = File(context.filesDir, "magnetic_zone_data.json")
    private val lock = Any()
    private val initial = runCatching { if (file.exists()) JsonDataCodec.decode(file.readText()) else StoredData() }
        .getOrElse { StoredData() }
    private val _zones = MutableStateFlow(initial.zones)
    override val zones = _zones.asStateFlow()
    private val _training = MutableStateFlow(initial.trainingSessions)
    override val trainingSessions = _training.asStateFlow()
    private val _experiments = MutableStateFlow(initial.experimentSessions)
    override val experimentSessions = _experiments.asStateFlow()
    private val _rules = MutableStateFlow(initial.automationRules)
    override val automationRules = _rules.asStateFlow()

    override suspend fun addZone(name: String): Zone = withContext(Dispatchers.IO) {
        val zone = Zone(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis())
        synchronized(lock) {
            _zones.value = _zones.value + zone
            persist()
        }
        zone
    }

    override suspend fun deleteZone(zoneId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _zones.value = _zones.value.filterNot { it.id == zoneId }
            _training.value = _training.value.filterNot { it.zoneId == zoneId }
            _experiments.value = _experiments.value.filterNot { it.actualZoneId == zoneId }
            persist()
        }
    }

    override suspend fun saveTrainingSession(session: TrainingSession) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _training.value = _training.value + session
            persist()
        }
    }

    override suspend fun saveExperimentSession(session: ExperimentSession) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _experiments.value = _experiments.value + session
            persist()
        }
    }

    override suspend fun saveAutomationRule(rule: AutomationRule) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _rules.value = if (_rules.value.any { it.id == rule.id }) {
                _rules.value.map { if (it.id == rule.id) rule else it }
            } else _rules.value + rule
            persist()
        }
    }

    override suspend fun deleteAutomationRule(ruleId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            _rules.value = _rules.value.filterNot { it.id == ruleId }
            persist()
        }
    }

    private fun persist() {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(JsonDataCodec.encode(StoredData(_zones.value, _training.value, _experiments.value, _rules.value)))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}

data class StoredData(
    val zones: List<Zone> = emptyList(),
    val trainingSessions: List<TrainingSession> = emptyList(),
    val experimentSessions: List<ExperimentSession> = emptyList(),
    val automationRules: List<AutomationRule> = listOf(AutomationRule.defaultStoveReminder()),
)
