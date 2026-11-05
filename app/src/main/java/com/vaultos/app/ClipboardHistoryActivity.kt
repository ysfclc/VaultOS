package com.vaultos.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultos.app.data.ClipboardHistoryEntry
import com.vaultos.app.storage.ClipboardHistoryRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ClipboardHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClipboardHistoryScreen(this) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardHistoryScreen(activity: ClipboardHistoryActivity) {
    val repository = remember { ClipboardHistoryRepository(activity) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    val entries by remember(query) { if (query.isBlank()) repository.observe() else repository.search(query) }
        .collectAsState(initial = emptyList())
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF9AE6C1), secondary = Color(0xFF8DBBFF),
        background = Color(0xFF0B0D10), surface = Color(0xFF14181E),
        surfaceVariant = Color(0xFF20262E), onSurface = Color(0xFFEAF0F4)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("Clipboard history", fontWeight = FontWeight.Bold)
                        Text("${entries.size} saved capture events", style = MaterialTheme.typography.labelSmall)
                    } },
                    actions = { TextButton(onClick = { clearDialog = true }) { Text("Clear") } }
                )
            },
            containerColor = Color(0xFF0B0D10)
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    singleLine = true, placeholder = { Text("Search copied text and source app") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                if (entries.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (query.isBlank()) "No clipboard captures yet" else "No matching captures", fontWeight = FontWeight.SemiBold)
                        Text("Select VaultOS Capture Keyboard to record clipboard changes. Android does not let ordinary apps read another keyboard’s clipboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            ClipboardEntryCard(entry, repository, activity,
                                onPin = { scope.launch { repository.setPinned(entry.id, !entry.isPinned) } },
                                onDelete = { scope.launch { repository.delete(entry.id) } }
                            )
                        }
                    }
                }
            }
        }
        if (clearDialog) AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear unpinned history?") },
            text = { Text("Pinned captures will be kept. The archived files remain in your VaultOS library.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.clearUnpinned(); clearDialog = false } }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ClipboardEntryCard(
    entry: ClipboardHistoryEntry,
    repository: ClipboardHistoryRepository,
    activity: ClipboardHistoryActivity,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember(entry.id) { mutableStateOf<String?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B22))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (entry.contentType == "text") "TEXT" else "FILE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(entry.foregroundPackage?.let { "Editor · $it" } ?: "Editor unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onPin) { Icon(Icons.Default.PushPin, contentDescription = if (entry.isPinned) "Unpin" else "Pin", tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove from history") }
            }
            Text(entry.preview.ifBlank { "(Empty clipboard item)" }, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.capturedAt))}  ·  ${if (entry.contentType == "text") "${entry.characterCount} chars · ${entry.byteCount} bytes" else "${entry.byteCount} bytes"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.contentType == "text") Button(onClick = {
                scope.launch {
                    val fullText = runCatching { repository.readText(entry) }.getOrNull()
                    if (fullText.isNullOrEmpty()) status = "Original text is unavailable"
                    else runCatching {
                        val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        manager.setPrimaryClip(ClipData.newPlainText("VaultOS history", fullText))
                    }.onSuccess { status = "Full text copied (${fullText.length} characters)" }
                        .onFailure { status = "Copy failed" }
                }
            }) { Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Copy full text") }
            status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
