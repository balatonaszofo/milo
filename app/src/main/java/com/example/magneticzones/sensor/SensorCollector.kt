package com.example.magneticzones.sensor

import com.example.magneticzones.model.RawSensorSample
import com.example.magneticzones.model.SensorStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SensorCollector {
    val samples: SharedFlow<RawSensorSample>
    val status: StateFlow<SensorStatus>
    fun start()
    fun stop()
}
