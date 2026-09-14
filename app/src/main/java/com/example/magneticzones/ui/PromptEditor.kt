package com.example.magneticzones.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticzones.model.AutomationRule
import com.example.magneticzones.model.RuleTrigger
import com.example.magneticzones.model.Zone
import java.util.UUID

@Composable
internal fun PromptEditor(existing: AutomationRule?, seed: String, seedTitle: String, zones: List<Zone>, onDismiss: () -> Unit, onSave: (AutomationRule) -> Unit) {
    var prompt by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(existing?.description ?: seed)) }
    var references by rememberSaveable(stateSaver = PromptReferencesSaver) { mutableStateOf(existing?.promptReferences.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(existing?.name ?: seedTitle) }
    var discard by remember { mutableStateOf(false) }
    val dirty = prompt.text != (existing?.description ?: seed) || references != existing?.promptReferences.orEmpty() || title != (existing?.name ?: seedTitle)
    val dismiss = { if (dirty) discard = true else onDismiss() }
    BackHandler { dismiss() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Atmosphere()
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = dismiss) { RuleIcon(RuleGlyph.Back, "Close editor", tint = Muted) }
                    Text(if (existing == null) "NEW REMINDER" else "REFINE REMINDER", Modifier.weight(1f).padding(start = 8.dp), color = Muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    RuleIcon(RuleGlyph.Heat, null, Modifier.padding(end = 16.dp), Butter)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Text("When should\nMilo check?", color = Cream, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp)
                    Text("Start with a moment away from the stove. Milo will check whether it's still on.", color = Muted, lineHeight = 23.sp)
                    MentionPromptField(prompt, references, zones) { next, linked -> prompt = next; references = linked }
                    if (existing == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Borrow an idea", color = Muted, fontSize = 12.sp)
                            ruleIdeas.forEach { idea ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { prompt = TextFieldValue(idea.prompt, TextRange(idea.prompt.length)); references = emptyList(); if (title.isBlank()) title = idea.title }.padding(vertical = 13.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RuleIcon(idea.glyph, null, Modifier.size(18.dp), Sage)
                                    Text(idea.title, Modifier.weight(1f), color = Cream)
                                    RuleIcon(RuleGlyph.Arrow, null, Modifier.size(16.dp), Muted)
                                }
                            }
                        }
                    }
                    OutlinedTextField(title, onValueChange = { if (it.length <= 80) title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Give it a name (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Text("Milo currently runs stove reminders that link one room with @Stove state. Other prompts are saved as drafts.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Button(
                    onClick = {
                        val base = existing ?: AutomationRule(UUID.randomUUID().toString(), "My rule", RuleTrigger.CUSTOM, null, true, System.currentTimeMillis())
                        onSave(base.withPrompt(title, prompt.text, references))
                    }, enabled = prompt.text.isNotBlank(), shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).heightIn(min = 56.dp),
                ) { Text(if (existing == null) "Save rule" else "Save changes", fontSize = 16.sp); Spacer(Modifier.width(12.dp)); RuleIcon(RuleGlyph.Arrow, null, Modifier.size(19.dp), Ink) }
            }
        }
        if (discard) AlertDialog(
            onDismissRequest = { discard = false }, title = { Text("Discard your changes?") },
            text = { Text("Your unsaved prompt will be lost.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep writing") } },
        )
    }
}
