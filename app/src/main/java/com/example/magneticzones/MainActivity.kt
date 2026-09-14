package com.example.magneticzones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.magneticzones.classification.NormalizedKnnClassifier
import com.example.magneticzones.data.FileZoneRepository
import com.example.magneticzones.processing.FeatureExtractor
import com.example.magneticzones.sensor.AndroidSensorCollector
import com.example.magneticzones.ui.AppViewModel
import com.example.magneticzones.ui.MiloApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(
                AndroidSensorCollector(applicationContext),
                FileZoneRepository(applicationContext),
                FeatureExtractor(),
                NormalizedKnnClassifier(),
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiloApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startSensors()
    }

    override fun onStop() {
        viewModel.stopSensors()
        super.onStop()
    }
}
