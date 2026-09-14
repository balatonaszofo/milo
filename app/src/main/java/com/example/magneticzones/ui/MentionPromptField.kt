package com.example.magneticzones.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.magneticzones.model.*

internal val PromptReferencesSaver = listSaver<List<PromptReference>, Any>(
    save = { refs -> refs.flatMap { listOf(it.start, it.label, it.kind.name, it.targetId) } },
    restore = { items -> items.chunked(4).map { PromptReference(it[0] as Int, it[1] as String, PromptReferenceKind.valueOf(it[2] as String), it[3] as String) } },
)

@Composable
internal fun MentionPromptField(value: TextFieldValue, references: List<PromptReference>, zones: List<Zone>, onChange: (TextFieldValue, List<PromptReference>) -> Unit) {
    val variables = remember(zones) { PromptMentions.variables(zones) }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf<MentionQuery?>(null) }
    var width by remember { mutableIntStateOf(0) }
    var tooLong by remember { mutableStateOf(false) }
    val query = PromptMentions.query(value.text, value.selection.start, value.selection.end, references)
    BackHandler(enabled = focused && query != null && query != dismissed) { dismissed = query }
    val matches = variables.filter { it.label.contains(query?.filter.orEmpty().trim(), ignoreCase = true) }
    val missing = references.any { ref -> variables.none { it.kind == ref.kind && it.targetId == ref.targetId } }
    val errorColor = MaterialTheme.colorScheme.error
    fun update(next: TextFieldValue) {
        if (next.text.length <= PromptMentions.MAX_LENGTH) {
            tooLong = false; dismissed = null
            onChange(next, PromptMentions.afterEdit(value.text, next.text, references, value.selection.start, value.selection.end))
        } else tooLong = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
            OutlinedTextField(
                value = value, onValueChange = ::update,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
                shape = RoundedCornerShape(20.dp), minLines = 3, maxLines = 6,
                placeholder = { Text("When I'm at @Couch, remind me if @Stove state is on.", lineHeight = 25.sp) },
                label = { Text("Describe your rule") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                supportingText = { Text("Type @ to link a room or value. Mentions stay highlighted.") },
                trailingIcon = {
                    IconButton(onClick = {
                        val start = value.selection.min
                        val end = value.selection.max
                        val prefix = if (start > 0 && !value.text[start - 1].isWhitespace()) " " else ""
                        update(TextFieldValue(value.text.replaceRange(start, end, "$prefix@"), TextRange(start + prefix.length + 1)))
                        focusRequester.requestFocus()
                    }, modifier = Modifier.semantics { contentDescription = "Insert variable" }) { Text("@", color = Butter, fontSize = 23.sp) }
                },
                visualTransformation = VisualTransformation { text ->
                    val highlighted = AnnotatedString.Builder(text)
                    references.filter { it.matches(text.text) }.forEach { ref ->
                        val color = if (variables.any { it.kind == ref.kind && it.targetId == ref.targetId }) Butter else errorColor
                        highlighted.addStyle(SpanStyle(color = color, background = color.copy(alpha = 0.12f), fontWeight = FontWeight.Medium), ref.start, ref.end)
                    }
                    TransformedText(highlighted.toAnnotatedString(), OffsetMapping.Identity)
                },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = WarmSurface, focusedContainerColor = WarmSurface, unfocusedBorderColor = Hairline),
            )
            DropdownMenu(
                expanded = focused && query != null && query != dismissed,
                onDismissRequest = { dismissed = query },
                modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }).heightIn(max = 232.dp),
                properties = PopupProperties(focusable = false),
                shape = RoundedCornerShape(16.dp), containerColor = WarmSurfaceHigh, border = BorderStroke(1.dp, Hairline),
            ) {
                if (matches.isEmpty()) {
                    Text("No matching rooms or values", Modifier.padding(16.dp), color = Muted)
                    Text("Add rooms in Settings → Locations.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Muted, fontSize = 12.sp)
                }
                matches.groupBy { it.kind }.forEach { (kind, options) ->
                    Text(if (kind == PromptReferenceKind.ROOM) "YOUR ROOMS" else "VALUES", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Butter, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    options.forEach { variable ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("@${variable.label}", color = Color.White)
                                    Text(variable.detail, color = Muted, fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                query?.let { current ->
                                    val insertion = PromptMentions.insert(value.text, current, variable, references)
                                    if (insertion != null) {
                                        onChange(TextFieldValue(insertion.text, TextRange(insertion.cursor)), insertion.references)
                                        tooLong = false
                                    } else tooLong = true
                                }
                                dismissed = query
                                focusRequester.requestFocus()
                            },
                        )
                    }
                }
            }
        }
        if (missing) Text("A linked room was removed. Replace its highlighted mention with an available room.", color = errorColor, fontSize = 12.sp)
        if (tooLong) Text("Keep your prompt under ${PromptMentions.MAX_LENGTH} characters to add more.", color = errorColor, fontSize = 12.sp)
    }
}
