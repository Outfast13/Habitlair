package com.app.journal

import com.app.habits.MediaAttachment

data class JournalEntry(
    val id: String,
    val title: String,
    val body: String,
    val mood: String,
    val timestampMs: Long,
    val attachments: List<MediaAttachment> = emptyList()
)

data class JournalState(
    val entries: List<JournalEntry> = emptyList()
)
