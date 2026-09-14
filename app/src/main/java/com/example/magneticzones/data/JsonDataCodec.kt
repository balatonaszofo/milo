package com.example.magneticzones.data

import com.example.magneticzones.model.*
import org.json.JSONArray
import org.json.JSONObject

object JsonDataCodec {
    fun encode(data: StoredData): String = JSONObject()
        .put("schemaVersion", 4)
        .put("zones", data.zones.toJsonArray(::zoneToJson))
        .put("trainingSessions", data.trainingSessions.toJsonArray(::trainingToJson))
        .put("experimentSessions", data.experimentSessions.toJsonArray(::experimentToJson))
        .put("automationRules", data.automationRules.toJsonArray(::ruleToJson))
        .toString()

    fun decode(text: String): StoredData {
        val root = JSONObject(text)
        val rules = if (root.has("automationRules")) {
            root.optJSONArray("automationRules").mapObjects(::ruleFromJson)
        } else {
            listOf(AutomationRule.defaultStoveReminder())
        }
        return StoredData(
            root.optJSONArray("zones").mapObjects(::zoneFromJson),
            root.optJSONArray("trainingSessions").mapObjects(::trainingFromJson),
            root.optJSONArray("experimentSessions").mapObjects(::experimentFromJson),
            rules,
        )
    }

    private fun ruleToJson(rule: AutomationRule) = JSONObject()
        .put("id", rule.id)
        .put("name", rule.name)
        .put("trigger", rule.trigger.name)
        .put("prompt", rule.prompt)
        .put("promptReferences", rule.promptReferences.toJsonArray { reference ->
            JSONObject().put("start", reference.start).put("label", reference.label)
                .put("kind", reference.kind.name).put("targetId", reference.targetId)
        })
        .put("locationName", rule.locationName ?: JSONObject.NULL)
        .put("enabled", rule.enabled)
        .put("createdAtMillis", rule.createdAtMillis)

    private fun ruleFromJson(json: JSONObject) = AutomationRule(
        id = json.getString("id"),
        name = json.getString("name"),
        trigger = runCatching { RuleTrigger.valueOf(json.getString("trigger")) }.getOrDefault(RuleTrigger.AT_LOCATION),
        locationName = json.nullableString("locationName"),
        enabled = json.optBoolean("enabled", true),
        createdAtMillis = json.optLong("createdAtMillis", 0L),
        prompt = json.optString("prompt", ""),
        promptReferences = json.optJSONArray("promptReferences").mapObjects { reference ->
            runCatching {
                PromptReference(reference.getInt("start"), reference.getString("label"),
                    PromptReferenceKind.valueOf(reference.getString("kind")), reference.getString("targetId"))
            }.getOrNull()
        }.filterNotNull().filter { it.matches(json.optString("prompt", "")) },
    )

    private fun zoneToJson(zone: Zone) = JSONObject()
        .put("id", zone.id).put("name", zone.name).put("createdAtMillis", zone.createdAtMillis)

    private fun zoneFromJson(json: JSONObject) = Zone(
        json.getString("id"), json.getString("name"), json.getLong("createdAtMillis")
    )

    private fun trainingToJson(session: TrainingSession) = JSONObject()
        .put("id", session.id)
        .put("zoneId", session.zoneId)
        .put("startedAtMillis", session.startedAtMillis)
        .put("endedAtMillis", session.endedAtMillis)
        .put("samples", session.samples.toJsonArray(::sampleToJson))
        .put("windows", session.windows.toJsonArray(::windowToJson))

    private fun trainingFromJson(json: JSONObject) = TrainingSession(
        json.getString("id"),
        json.getString("zoneId"),
        json.getLong("startedAtMillis"),
        json.getLong("endedAtMillis"),
        json.getJSONArray("samples").mapObjects(::sampleFromJson),
        json.getJSONArray("windows").mapObjects(::windowFromJson),
    )

    private fun experimentToJson(session: ExperimentSession) = JSONObject()
        .put("id", session.id)
        .put("actualZoneId", session.actualZoneId)
        .put("startedAtMillis", session.startedAtMillis)
        .put("endedAtMillis", session.endedAtMillis)
        .put("samples", session.samples.toJsonArray(::sampleToJson))
        .put("windows", session.windows.toJsonArray(::windowToJson))
        .put("predictions", session.predictions.toJsonArray(::predictionToJson))

    private fun experimentFromJson(json: JSONObject) = ExperimentSession(
        json.getString("id"),
        json.getString("actualZoneId"),
        json.getLong("startedAtMillis"),
        json.getLong("endedAtMillis"),
        json.getJSONArray("samples").mapObjects(::sampleFromJson),
        json.getJSONArray("windows").mapObjects(::windowFromJson),
        json.getJSONArray("predictions").mapObjects(::predictionFromJson),
    )

    private fun sampleToJson(sample: RawSensorSample) = JSONObject()
        .put("timestampNanos", sample.timestampNanos)
        .put("wallClockMillis", sample.wallClockMillis)
        .put("magnetic", vectorToJson(sample.magnetic))
        .put("acceleration", sample.acceleration?.let(::vectorToJson) ?: JSONObject.NULL)
        .put("gyroscope", sample.gyroscope?.let(::vectorToJson) ?: JSONObject.NULL)
        .put("orientation", sample.orientation?.let(::orientationToJson) ?: JSONObject.NULL)

    private fun sampleFromJson(json: JSONObject) = RawSensorSample(
        json.getLong("timestampNanos"),
        json.getLong("wallClockMillis"),
        vectorFromJson(json.getJSONObject("magnetic")),
        json.nullableObject("acceleration")?.let(::vectorFromJson),
        json.nullableObject("gyroscope")?.let(::vectorFromJson),
        json.nullableObject("orientation")?.let(::orientationFromJson),
    )

    private fun vectorToJson(vector: Vector3) = JSONObject()
        .put("x", vector.x.toDouble()).put("y", vector.y.toDouble()).put("z", vector.z.toDouble())

    private fun vectorFromJson(json: JSONObject) = Vector3(
        json.getDouble("x").toFloat(), json.getDouble("y").toFloat(), json.getDouble("z").toFloat()
    )

    private fun orientationToJson(value: Orientation) = JSONObject()
        .put("azimuth", value.azimuthDegrees.toDouble())
        .put("pitch", value.pitchDegrees.toDouble())
        .put("roll", value.rollDegrees.toDouble())

    private fun orientationFromJson(json: JSONObject) = Orientation(
        json.getDouble("azimuth").toFloat(),
        json.getDouble("pitch").toFloat(),
        json.getDouble("roll").toFloat(),
    )

    private fun windowToJson(window: FeatureWindow): JSONObject {
        val values = JSONObject()
        window.values.forEach { (key, value) -> values.put(key, value) }
        return JSONObject()
            .put("startNanos", window.startNanos)
            .put("endNanos", window.endNanos)
            .put("sampleCount", window.sampleCount)
            .put("values", values)
    }

    private fun windowFromJson(json: JSONObject): FeatureWindow {
        val valuesJson = json.getJSONObject("values")
        val values = valuesJson.keys().asSequence().associateWith { valuesJson.getDouble(it) }
        return FeatureWindow(
            json.getLong("startNanos"),
            json.getLong("endNanos"),
            json.getInt("sampleCount"),
            values,
        )
    }

    private fun predictionToJson(value: PredictionRecord) = JSONObject()
        .put("timestampNanos", value.timestampNanos)
        .put("rawZoneId", value.rawZoneId ?: JSONObject.NULL)
        .put("smoothedZoneId", value.smoothedZoneId ?: JSONObject.NULL)
        .put("activeZoneId", value.activeZoneId ?: JSONObject.NULL)
        .put("confidence", value.confidence)

    private fun predictionFromJson(json: JSONObject) = PredictionRecord(
        json.getLong("timestampNanos"),
        json.nullableString("rawZoneId"),
        json.nullableString("smoothedZoneId"),
        json.nullableString("activeZoneId"),
        json.getDouble("confidence"),
    )

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject) =
        JSONArray().also { array -> forEach { array.put(mapper(it)) } }

    private fun <T> JSONArray?.mapObjects(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { mapper(getJSONObject(it)) }
    }

    private fun JSONObject.nullableObject(key: String) = if (isNull(key)) null else getJSONObject(key)
    private fun JSONObject.nullableString(key: String) = if (isNull(key)) null else getString(key)
}
