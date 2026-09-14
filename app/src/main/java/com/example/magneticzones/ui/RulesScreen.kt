package com.example.magneticzones.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticzones.model.AutomationRule
import com.example.magneticzones.R
import com.example.magneticzones.automation.RuleCompilation
import com.example.magneticzones.automation.RuleCompiler
import com.example.magneticzones.automation.RuleMonitorService
import com.example.magneticzones.automation.RuleMonitorSnapshot
import com.example.magneticzones.automation.TrackerConnection
import com.example.magneticzones.model.RuleTrigger
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone

internal data class RuleIdea(val title: String, val prompt: String, val glyph: RuleGlyph)
internal val ruleIdeas = listOf(
    RuleIdea("Heading out", "When I reach the front door, remind me if the stove is still on.", RuleGlyph.Arrow),
    RuleIdea("Settling in", "When I sit on the couch for 10 seconds, remind me if the stove is still on.", RuleGlyph.Spark),
    RuleIdea("Calling it a night", "When I get to the bedroom, remind me if the stove is still on.", RuleGlyph.Heat),
)

@Composable
fun RulesScreen(viewModel: AppViewModel, modifier: Modifier = Modifier, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val rules by viewModel.automationRules.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val training by viewModel.trainingSessions.collectAsState()
    val monitor by RuleMonitorService.snapshot.collectAsState()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var seed by rememberSaveable { mutableStateOf("") }
    var seedTitle by rememberSaveable { mutableStateOf("") }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    fun createRule(prompt: String = "", title: String = "") {
        seed = prompt; seedTitle = title; editingId = null; editorOpen = true
    }

    Box(modifier.fillMaxSize().background(Ink)) {
        if (editorOpen) {
            PromptEditor(
                existing = rules.firstOrNull { it.id == editingId }, seed = seed, seedTitle = seedTitle,
                zones = zones,
                onDismiss = { editorOpen = false }, onSave = { viewModel.saveAutomationRule(it); editorOpen = false },
            )
        } else {
        Atmosphere()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_magnetic_mark), contentDescription = null, modifier = Modifier.size(36.dp))
                Text("milo", Modifier.padding(start = 10.dp).weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp, color = Color.White)
                IconButton(onClick = onSettings) { RuleIcon(RuleGlyph.Settings, "Settings", tint = Muted) }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("A little help in the kitchen", color = Butter, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Your little\nsous chef.", color = Cream, fontSize = 38.sp, lineHeight = 43.sp, letterSpacing = (-1.2).sp)
                        Text("A friendly nudge when\nthe stove's still on.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your reminders", color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(rules.size.toString().padStart(2, '0'), color = Muted, fontSize = 12.sp)
                    }
                }
                if (rules.isEmpty()) item {
                    Column(
                        Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuleIcon(RuleGlyph.Heat, null, tint = Butter)
                        Text("When should Milo check?", color = Cream, style = MaterialTheme.typography.titleMedium)
                        Text("Choose a moment away from the stove and Milo can remind you if it's still on.", color = Muted, lineHeight = 22.sp)
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule, ruleStatus(rule, zones, training, monitor),
                        onEdit = { editingId = rule.id; editorOpen = true },
                        onEnabledChange = { viewModel.setAutomationRuleEnabled(rule, it) },
                        onTestAlert = { RuleMonitorService.sendTestAlert(context, rule.id) },
                        onDelete = { deletingId = rule.id },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        RuleIcon(RuleGlyph.Info, null, Modifier.size(15.dp), Muted)
                        Text(monitorSummary(rules, monitor), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(Modifier.height(4.dp))
                        Text("Try one of these", color = Muted, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ruleIdeas.forEach { idea ->
                                Surface(
                                    onClick = { createRule(idea.prompt, idea.title) }, color = WarmSurface,
                                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Hairline),
                                ) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RuleIcon(idea.glyph, null, Modifier.size(17.dp), Sage)
                                        Text(idea.title, color = Cream, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Ink))).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) {
                Surface(
                    onClick = { createRule() }, color = Color.Transparent, shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Butter.copy(alpha = 0.58f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF342A21), Color(0xFF29241E)))).padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuleIcon(RuleGlyph.Heat, null, Modifier.size(25.dp), Butter)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Set a stove reminder", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text("Tell Milo when to check in.", color = Muted, fontSize = 11.sp)
                        }
                        Box(Modifier.size(36.dp).background(Butter, CircleShape), contentAlignment = Alignment.Center) {
                            RuleIcon(RuleGlyph.Add, null, Modifier.size(20.dp), Ink)
                        }
                    }
                }
            }
        }
        }
    val deleting = rules.firstOrNull { it.id == deletingId }
    if (deleting != null) AlertDialog(
        onDismissRequest = { deletingId = null }, title = { Text("Delete " + deleting.name + "?") },
        text = { Text("This removes the saved rule from this phone.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteAutomationRule(deleting.id); deletingId = null }) { Text("Delete rule", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Keep rule") } },
    )
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    status: RuleCardStatus,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = if (rule.enabled) Butter else Muted
    Surface(
        onClick = onEdit, color = Color.Transparent, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF514238), Color(0xFF342C27)))),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(WarmSurfaceHigh, WarmSurface)))
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.09f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    RuleIcon(if (rule.trigger == RuleTrigger.CUSTOM) RuleGlyph.Spark else RuleGlyph.Heat, null, tint = accent)
                }
                Text(rule.name, Modifier.weight(1f), color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Switch(
                    rule.enabled, onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "Enable " + rule.name },
                    colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Butter, uncheckedThumbColor = Muted, uncheckedTrackColor = Color(0xFF453B35)),
                )
            }
            val description = buildAnnotatedString {
                append(rule.description)
                rule.promptReferences.filter { it.matches(rule.description) }.forEach {
                    addStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.10f)), it.start, it.end)
                }
            }
            Text(description, color = if (rule.enabled) Cream else Muted, fontSize = 18.sp, lineHeight = 27.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule.promptReferences.isNotEmpty()) {
                    rule.promptReferences.distinctBy { it.kind to it.targetId }.forEach { RuleTag(it.label) }
                } else if (rule.trigger != RuleTrigger.CUSTOM) {
                    RuleTag(rule.locationName ?: "Heading out")
                    RuleTag("Stove Tracker")
                } else RuleTag("Custom prompt")
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(if (status.active) Sage else if (rule.enabled) Butter else Muted, CircleShape))
                Text(status.label, Modifier.padding(start = 7.dp).weight(1f), color = if (status.active) Sage else Muted, fontSize = 11.sp)
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Refine rule", color = accent, fontSize = 12.sp) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { RuleIcon(RuleGlyph.More, "More options for " + rule.name, tint = Muted) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit prompt") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Send test alert") }, onClick = { menu = false; onTestAlert() })
                        DropdownMenuItem(text = { Text("Delete rule", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleTag(label: String) {
    Text(label, Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.045f)).padding(horizontal = 9.dp, vertical = 6.dp), color = Muted, fontSize = 10.sp)
}

private data class RuleCardStatus(val label: String, val active: Boolean = false)

private fun ruleStatus(rule: AutomationRule, zones: List<Zone>, training: List<TrainingSession>, monitor: RuleMonitorSnapshot): RuleCardStatus {
    if (!rule.enabled) return RuleCardStatus("Paused")
    return when (val compilation = RuleCompiler.compile(rule, zones, training)) {
        is RuleCompilation.Incomplete -> RuleCardStatus("Needs setup · ${compilation.message}")
        is RuleCompilation.Ready -> when {
            !monitor.notificationsAllowed -> RuleCardStatus("Needs notification permission")
            !monitor.running -> RuleCardStatus("Starting monitor…")
            monitor.trackerConnection == TrackerConnection.OFFLINE -> RuleCardStatus("Camera Tracker unavailable")
            monitor.trackerConnection == TrackerConnection.CHECKING -> RuleCardStatus("Connecting to Camera Tracker…")
            else -> RuleCardStatus("Active · monitoring", active = true)
        }
    }
}

private fun monitorSummary(rules: List<AutomationRule>, monitor: RuleMonitorSnapshot): String = when {
    rules.none { it.enabled } -> "All rules are paused."
    !monitor.notificationsAllowed -> "Allow notifications so Milo can deliver enabled rules."
    !monitor.running -> "Starting background monitoring…"
    monitor.trackerConnection == TrackerConnection.OFFLINE -> "Camera Tracker is unavailable at home. Milo will keep retrying."
    else -> "Camera Tracker connected · background monitoring is on."
}
