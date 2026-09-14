package com.example.magneticzones.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.magneticzones.BuildConfig
import com.example.magneticzones.automation.RuleMonitorService

private enum class AppScreen(val label: String, val detail: String = "") {
    Rules("Rules"), Settings("Settings"),
    Locations("Locations", "Teach Milo the places in your home"),
    Recognition("Recognition", "See which location your phone recognizes"),
    Explorer("Sensors", "Explore magnetic fields and movement"),
    Experiments("Experiments", "Test how reliably locations are recognized"),
    Diagnostics("Diagnostics", "Inspect results and export your data")
}

internal val Ink = Color(0xFF15110F)
internal val Butter = Color(0xFFF1C982)
internal val Sage = Color(0xFFA9C3A2)
internal val Cream = Color(0xFFF6F0E7)
internal val Muted = Color(0xFFB4A79D)
internal val Hairline = Color(0xFF433831)
internal val WarmSurface = Color(0xFF221C19)
internal val WarmSurfaceHigh = Color(0xFF2B2420)
internal val Ember = Color(0xFFE47C61)

private val MiloDarkColors = darkColorScheme(
    primary = Butter, onPrimary = Color(0xFF3B2A10),
    primaryContainer = Color(0xFF4A3820), onPrimaryContainer = Color(0xFFFFE1A8),
    secondary = Sage, onSecondary = Color(0xFF20331F),
    background = Ink, surface = WarmSurface,
    onBackground = Cream, onSurface = Cream,
    onSurfaceVariant = Muted, surfaceVariant = WarmSurfaceHigh, outline = Hairline,
    error = Ember, onError = Color(0xFF3D1008),
    errorContainer = Color(0xFF4A251F), onErrorContainer = Color(0xFFFFDAD2),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiloApp(viewModel: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Rules) }
    val context = LocalContext.current
    val rules by viewModel.automationRules.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val monitoringEnabled = rules.any { it.enabled }
    LaunchedEffect(monitoringEnabled) {
        if (monitoringEnabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RuleMonitorService.setEnabled(context, monitoringEnabled)
    }
    val goBack = { screen = if (screen == AppScreen.Settings) AppScreen.Rules else AppScreen.Settings }
    BackHandler(enabled = screen != AppScreen.Rules) { goBack() }
    MaterialTheme(colorScheme = MiloDarkColors) {
        if (screen == AppScreen.Rules) {
            RulesScreen(viewModel, onSettings = { screen = AppScreen.Settings })
        } else {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text(screen.label) },
                    navigationIcon = { IconButton(onClick = goBack) { RuleIcon(RuleGlyph.Back, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                )
            }) { padding ->
                val modifier = Modifier.fillMaxSize().padding(padding)
                when (screen) {
                    AppScreen.Settings -> Column(
                        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Make yourself at home.", style = MaterialTheme.typography.headlineSmall)
                        Text("Set up locations and see how Milo recognizes them.", color = Muted)
                        Spacer(Modifier.height(12.dp))
                        AppScreen.entries.filter { it !in listOf(AppScreen.Rules, AppScreen.Settings) }.forEach { destination ->
                            Surface(
                                onClick = { screen = destination }, shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(destination.label, style = MaterialTheme.typography.titleMedium)
                                        Text(destination.detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    RuleIcon(RuleGlyph.Arrow, null, tint = Butter)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("MILO  /  ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    AppScreen.Explorer -> ExplorerScreen(viewModel, modifier)
                    AppScreen.Locations -> LocationsScreen(viewModel, modifier)
                    AppScreen.Recognition -> RecognitionScreen(viewModel, modifier)
                    AppScreen.Experiments -> ExperimentScreen(viewModel, modifier)
                    AppScreen.Diagnostics -> DiagnosticsScreen(viewModel, modifier)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

fun Double.percent() = "%.1f%%".format(this * 100)
fun Number.decimals(count: Int = 2) = ("% ." + count + "f").format(toDouble()).trim()
