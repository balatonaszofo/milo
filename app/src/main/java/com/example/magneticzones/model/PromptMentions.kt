package com.example.magneticzones.model

enum class PromptReferenceKind { ROOM, VALUE }

data class PromptReference(val start: Int, val label: String, val kind: PromptReferenceKind, val targetId: String) {
    val token: String get() = "@$label"
    val end: Int get() = start + token.length
    fun matches(text: String) = start in 0..text.length && end in start..text.length && text.substring(start, end) == token
}

data class PromptVariable(val label: String, val kind: PromptReferenceKind, val targetId: String, val detail: String)
data class MentionQuery(val start: Int, val end: Int, val filter: String)
data class MentionInsertion(val text: String, val cursor: Int, val references: List<PromptReference>)

object PromptMentions {
    const val MAX_LENGTH = 2000

    fun variables(zones: List<Zone>): List<PromptVariable> = zones.sortedBy { it.name.lowercase() }.map { zone ->
        val duplicate = zones.count { it.name.equals(zone.name, ignoreCase = true) } > 1
        PromptVariable(zone.name, PromptReferenceKind.ROOM, zone.id,
            if (duplicate) "Saved room · ${zone.id.take(8)}" else "Saved room")
    } + listOf(
        PromptVariable("Current room", PromptReferenceKind.VALUE, "location.current_room", "The room your phone recognizes"),
        PromptVariable("Stove state", PromptReferenceKind.VALUE, "stove.state", "Live on/off state from Camera Tracker"),
    )

    fun query(text: String, selectionStart: Int, selectionEnd: Int, references: List<PromptReference>): MentionQuery? {
        if (selectionStart != selectionEnd || selectionStart !in 0..text.length) return null
        val cursor = selectionStart
        if (references.any { it.matches(text) && cursor > it.start && cursor <= it.end }) return null
        val start = text.lastIndexOf('@', (cursor - 1).coerceAtLeast(0))
        if (start < 0 || start >= cursor) return null
        if (references.any { it.start == start && it.matches(text) }) return null
        if (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] !in "([{") return null
        val filter = text.substring(start + 1, cursor)
        if (filter.length > 80 || filter.any { !it.isLetterOrDigit() && it !in " _-" }) return null
        return MentionQuery(start, cursor, filter)
    }

    /** Keep IDs attached only to untouched tokens, moving their ranges after edits. */
    fun afterEdit(old: String, new: String, references: List<PromptReference>, selectionStart: Int? = null, selectionEnd: Int? = selectionStart): List<PromptReference> {
        if (old == new) return references.filter { it.matches(new) }
        var prefix = 0
        while (prefix < minOf(old.length, new.length) && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(old.length, new.length) - prefix && old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
        var oldEnd = old.length - suffix
        val delta = new.length - old.length
        // Prefer the actual selection to a text diff when identical mentions occur twice.
        fun matchesEdit(start: Int, end: Int): Boolean {
            val inserted = new.length - (old.length - (end - start))
            return start in 0..old.length && end in start..old.length && inserted >= 0 &&
                start + inserted <= new.length && old.substring(0, start) == new.substring(0, start) &&
                old.substring(end) == new.substring(start + inserted)
        }
        if (selectionStart != null && selectionEnd != null) {
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            val candidates = if (start != end || delta >= 0) listOf(start to end)
                else listOf((start + delta) to end, start to (end - delta))
            candidates.firstOrNull { matchesEdit(it.first, it.second) }?.let { prefix = it.first; oldEnd = it.second }
        }
        return references.filter { it.matches(old) }.mapNotNull { reference ->
            when {
                reference.end <= prefix -> reference
                reference.start >= oldEnd -> reference.copy(start = reference.start + delta)
                else -> null
            }
        }.filter { it.matches(new) }
    }

    fun insert(text: String, query: MentionQuery, variable: PromptVariable, references: List<PromptReference>): MentionInsertion? {
        if (query.start !in text.indices || query.end !in query.start + 1..text.length || text[query.start] != '@') return null
        val token = "@${variable.label}"
        val following = text.getOrNull(query.end)
        val separator = if (following == null || (!following.isWhitespace() && following !in ".,;:!?)]}")) " " else ""
        val result = text.replaceRange(query.start, query.end, token + separator)
        if (result.length > MAX_LENGTH) return null
        val updated = afterEdit(text, result, references) + PromptReference(query.start, variable.label, variable.kind, variable.targetId)
        return MentionInsertion(result, query.start + token.length + separator.length, updated.sortedBy { it.start })
    }
}
