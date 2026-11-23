package com.vaultos.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.util.LruCache
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultItem
import com.vaultos.app.data.VaultDao
import com.vaultos.app.data.UserTag
import com.vaultos.app.data.ItemTagCrossRef
import com.vaultos.app.data.UserCollection
import com.vaultos.app.data.CollectionItemCrossRef
import com.vaultos.app.data.SavedSearch
import com.vaultos.app.keyboard.VaultKeyboardService
import com.vaultos.app.storage.StorageScanWorker
import com.vaultos.app.storage.VaultBackupManager
import com.vaultos.app.storage.FileOperationProgress
import com.vaultos.app.storage.FileOperations
import com.vaultos.app.storage.VaultRepository
import com.vaultos.app.storage.UsbOtgDiagnostics
import com.vaultos.app.storage.ClipboardHistoryRepository
import com.vaultos.app.storage.MetadataExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val usbPermissionAction = "com.vaultos.app.USB_PERMISSION"
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != usbPermissionAction) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            resumeRevision++
            Toast.makeText(this@MainActivity, if (granted) "USB access granted. Interface details are now available." else "USB access was not granted.", Toast.LENGTH_SHORT).show()
        }
    }
    private var usbReceiverRegistered = false

    lateinit var repository: VaultRepository
        private set
    var resumeRevision by mutableIntStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultRepository(this)
        val usbFilter = IntentFilter(usbPermissionAction)
        ContextCompat.registerReceiver(this, usbPermissionReceiver, usbFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        usbReceiverRegistered = true
        handleIncoming(intent)
        scheduleScanner()
        setContent { VaultApp(this, repository, resumeRevision) }
    }

    override fun onResume() {
        super.onResume()
        resumeRevision++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val prefs = getSharedPreferences(StorageScanWorker.PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong("last_foreground_scan_request", 0L)
            if (now - last >= TimeUnit.MINUTES.toMillis(15)) {
                prefs.edit().putLong("last_foreground_scan_request", now).apply()
                val request = OneTimeWorkRequestBuilder<StorageScanWorker>().build()
                WorkManager.getInstance(this).enqueueUniqueWork("vaultos-storage-scan-now", ExistingWorkPolicy.KEEP, request)
            }
        }
    }

    fun requestUsbPermission(deviceName: String) {
        val manager = getSystemService(Context.USB_SERVICE) as? UsbManager
        val device = manager?.deviceList?.get(deviceName)
        if (manager == null || device == null) {
            Toast.makeText(this, "USB device is no longer connected. Refresh detection.", Toast.LENGTH_SHORT).show()
            resumeRevision++
            return
        }
        if (manager.hasPermission(device)) {
            resumeRevision++
            return
        }
        val permissionIntent = PendingIntent.getBroadcast(
            this, deviceName.hashCode(), Intent(usbPermissionAction).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPermission(device, permissionIntent)
    }

    override fun onDestroy() {
        if (usbReceiverRegistered) {
            unregisterReceiver(usbPermissionReceiver)
            usbReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    if (uri != null) runCatching { repository.importUri(uri, "Shared from ${intent.type ?: "another app"}") }
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let {
                        runCatching { repository.saveText(it, "Shared from another app") }
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    @Suppress("DEPRECATION")
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                    uris.distinct().forEach { uri -> runCatching { repository.importUri(uri, "Shared batch") } }
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let {
                        runCatching { repository.saveText(it, "Shared with files") }
                    }
                }
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let {
                    runCatching { repository.saveText(it, "Selected text") }
                }
                else -> Unit
            }
        }
    }

    private fun scheduleScanner() {
        val periodic = PeriodicWorkRequestBuilder<StorageScanWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("vaultos-storage-scan", ExistingPeriodicWorkPolicy.KEEP, periodic)
        val initial = OneTimeWorkRequestBuilder<StorageScanWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork("vaultos-storage-scan-now", ExistingWorkPolicy.KEEP, initial)
    }
}

private enum class Page(val label: String) { Home("Home"), Incoming("Library"), Files("Files"), Collections("Organize"), More("More"), Apps("Apps"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultApp(activity: MainActivity, repository: VaultRepository, resumeRevision: Int) {
    val dao = remember { VaultDatabase.get(activity).dao() }
    val items by dao.observeAll().collectAsState(initial = emptyList())
    val trashItems by dao.observeTrash().collectAsState(initial = emptyList())
    val count by dao.count().collectAsState(initial = 0)
    val bytes by dao.archivedBytes().collectAsState(initial = 0L)
    var page by remember { mutableStateOf(Page.Home) }
    var query by remember { mutableStateOf("") }
    var activeSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(250); activeSearchQuery = query.trim() }
    var searchMode by remember { mutableStateOf(false) }
    var showItem by remember { mutableStateOf<VaultItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val searchItems by remember(activeSearchQuery) { if (activeSearchQuery.isBlank()) flowOf(emptyList()) else dao.search(activeSearchQuery) }.collectAsState(initial = emptyList())
    val uiScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> activity.lifecycleScope.launch {
            runCatching { repository.importUri(uri, "Manual import") }
                .onFailure { message = it.message ?: "Import failed" }
                .onSuccess { message = "Saved ${it.name}" }
        } }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            activity.getSharedPreferences(VaultKeyboardService.PREFS, Context.MODE_PRIVATE).edit().putString("backup_tree", uri.toString()).apply()
            message = "Backup destination saved"
        }
    }
    val permissionGranted = remember(resumeRevision) { hasAllFilesAccess(activity) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); message = null } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF9AE6C1), secondary = Color(0xFF8DBBFF),
        background = Color(0xFF0B0D10), surface = Color(0xFF14181E),
        surfaceVariant = Color(0xFF20262E), onSurface = Color(0xFFEAF0F4)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column { Text("VaultOS", fontWeight = FontWeight.Bold); Text("Your phone. One library.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    actions = {
                        IconButton(onClick = { captureClipboard(activity, repository, onMessage = { message = it }) }) { Icon(Icons.Default.ContentPaste, "Capture clipboard") }
                        IconButton(onClick = { activity.startActivity(Intent(activity, ClipboardHistoryActivity::class.java)) }) { Icon(Icons.Default.History, "Clipboard history") }
                        IconButton(onClick = { if (searchMode) query = ""; searchMode = !searchMode }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, "Import files") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0D10))
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF11151A)) {
                    listOf(Page.Home, Page.Incoming, Page.Files, Page.Collections, Page.More).forEach { p ->
                        val icon = when (p) {
                            Page.Home -> Icons.Default.Home
                            Page.Incoming -> Icons.Default.Inbox
                            Page.Files -> Icons.Default.Folder
                            Page.Collections -> Icons.Default.Bookmarks
                            Page.Apps -> Icons.Default.Android
                            Page.Settings -> Icons.Default.Settings
                            Page.More -> Icons.Default.MoreHoriz
                        }
                        val selected = if (p == Page.More) page in setOf(Page.More, Page.Apps, Page.Settings) else page == p
                        NavigationBarItem(selected = selected, onClick = { page = p }, icon = { Icon(icon, p.label) }, label = { Text(p.label) })
                    }
                }
            },
            containerColor = Color(0xFF0B0D10),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).animateContentSize()) {
                if (searchMode) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = { Text("Search names, full text, tags, metadata…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                    )
                    if (query.isNotBlank()) {
                        TextButton(onClick = {
                            val name = query.trim().take(60)
                            val normalized = name.lowercase(Locale.ROOT)
                            uiScope.launch {
                                dao.insertSavedSearch(SavedSearch(UUID.randomUUID().toString(), name, normalized, query.trim(), System.currentTimeMillis()))
                                message = "Search saved"
                            }
                        }) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(6.dp)); Text("Save search") }
                    }
                }
                if (searchMode) {
                    when {
                        query.isBlank() -> EmptyState(
                            "Search your library",
                            "Search file names, full text, tags and extracted metadata. Results stay on this device."
                        )
                        searchItems.isEmpty() -> EmptyState(
                            "No matches found",
                            "Try another file name, phrase, tag or metadata term."
                        )
                        else -> Column(
                            Modifier.fillMaxSize().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Search results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${searchItems.size} MATCHES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyColumn(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(searchItems, key = { it.id }) { item ->
                                    ItemRow(
                                        item,
                                        onClick = { showItem = item },
                                        onPin = { activity.lifecycleScope.launch { repository.setPinned(item, !item.isPinned) } }
                                    )
                                }
                            }
                        }
                    }
                } else when (page) {
                    Page.Home -> HomePage(
                        count = count,
                        bytes = bytes,
                        available = storageAvailable(),
                        recent = items.take(8),
                        permissionGranted = permissionGranted,
                        onPage = { page = it },
                        onItem = { showItem = it },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onCapture = { captureClipboard(activity, repository) { message = it } },
                        onGrantAccess = { openStorageSettings(activity) }
                    )
                    Page.Incoming -> ItemList(items, onItem = { showItem = it }, onPin = { item, pinned -> activity.lifecycleScope.launch { repository.setPinned(item, pinned) } })
                    Page.Files -> FilesPage(activity, repository, items.filter { it.category != "Text" }, trashItems, permissionGranted, onItem = { showItem = it }, onPermission = { openStorageSettings(activity) }, onMessage = { message = it })
                    Page.Collections -> CollectionsPage(
                        dao = dao,
                        items = items,
                        onCategory = { query = it; searchMode = true; page = Page.Incoming },
                        onTag = { query = it; searchMode = true; page = Page.Incoming },
                        onSavedSearch = { query = it; searchMode = true; page = Page.Incoming },
                        onItem = { showItem = it },
                        onMessage = { message = it }
                    )
                    Page.More -> MorePage(onApps = { page = Page.Apps }, onSettings = { page = Page.Settings })
                    Page.Apps -> AppsPage(activity, repository, onMessage = { message = it })
                    Page.Settings -> SettingsPage(activity, repository, permissionGranted, backupLauncher, onMessage = { message = it })
                }
            }
        }

        showItem?.let { item ->
            val isText = item.category == "Text" || item.category == "Code"
            var isCopying by remember(item.id) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { if (!isCopying) showItem = null },
                title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(
                        Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${item.category} · ${formatBytes(item.sizeBytes)} · ${if (item.isArchived) "Archived" else "Indexed"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall)
                        Text(item.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.category == "Images" || item.category == "Videos" || item.category == "Audio" || item.name.endsWith(".pdf", true)) ItemPreview(item, 320)
                        if (isText) Text(item.textContent?.take(3000).orEmpty().ifBlank { "Text content is preserved in the original file." }, maxLines = 16, overflow = TextOverflow.Ellipsis)
                        else if (item.category != "Images") Text(item.archivedPath ?: item.originalUri ?: "Indexed item", maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (isText && item.sizeBytes > 3000) Text("The complete ${item.sizeBytes} byte original is preserved. Copy reads the full file only when requested.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = Color(0xFF303943))
                        ItemOrganizerSection(dao, item, uiScope)
                        HorizontalDivider(color = Color(0xFF303943))
                        ItemMetadataSection(activity, dao, item, uiScope, onMessage = { message = it })
        HorizontalDivider(color = Color(0xFF303943))
        ItemRelatedSection(dao, item, onItem = { showItem = it })
                    }
                },
                confirmButton = {
                    TextButton(enabled = !isCopying, onClick = {
                        if (isText) {
                            isCopying = true
                            activity.lifecycleScope.launch {
                                val fullText = runCatching { withContext(Dispatchers.IO) { repository.readFullText(item) } }.getOrNull()
                                if (fullText.isNullOrEmpty()) message = "The full text source is no longer available"
                                else {
                                    runCatching {
                                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("VaultOS · ${item.name}", fullText))
                                    }.onSuccess { message = "Copied complete content · ${fullText.length} characters" }
                                        .onFailure { message = "Clipboard rejected this text; use Export or copy smaller sections" }
                                }
                                isCopying = false
                                showItem = null
                            }
                        } else {
                            openItem(activity, item, onMessage = { message = it })
                            showItem = null
                        }
                    }) { Text(if (isCopying) "Loading…" else if (isText) "Copy full text" else "Open") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            activity.lifecycleScope.launch { repository.setPinned(item, !item.isPinned) }
                        }) { Text(if (item.isPinned) "Unpin" else "Pin") }
                        TextButton(onClick = { shareItem(activity, item, onMessage = { message = it }); showItem = null }) { Text("Share") }
                        if (item.isArchived && !item.isTrashed) TextButton(onClick = {
                            activity.lifecycleScope.launch {
                                runCatching { repository.moveToTrash(item) }
                                    .onSuccess { message = if (it) "Moved to Recycle Bin" else "Could not move item to Recycle Bin" }
                                    .onFailure { message = it.message ?: "Recycle Bin action failed" }
                            }
                            showItem = null
                        }) { Text("Move to trash") }
                        TextButton(onClick = { showItem = null }) { Text("Close") }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomePage(count: Int, bytes: Long, available: Long, recent: List<VaultItem>, permissionGranted: Boolean, onPage: (Page) -> Unit, onItem: (VaultItem) -> Unit, onImport: () -> Unit, onCapture: () -> Unit, onGrantAccess: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18251F)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YOUR PERSONAL FILE SYSTEM", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium)
                Text("Everything in.\nNothing lost.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Metric("LIBRARY ITEMS", count.toString())
                    Metric("ARCHIVED", formatBytes(bytes))
                    Metric("DEVICE FREE", formatBytes(available))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickTile("Capture", Icons.Default.ContentPaste, Modifier.weight(1f), onCapture)
            QuickTile("Import", Icons.Default.Add, Modifier.weight(1f), onImport)
            QuickTile("Browse", Icons.Default.FolderOpen, Modifier.weight(1f)) { onPage(Page.Files) }
        }
        if (!permissionGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF8DBBFF), modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Connect your device library", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow VaultOS to index your shared folders and make existing files searchable. Your originals stay where they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = onGrantAccess, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Enable")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recently added", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onPage(Page.Incoming) }) { Text("See all") }
        }
        if (recent.isEmpty()) EmptyState("Your library starts here", "Share a file to VaultOS or import documents, photos, APKs and text.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(recent, key = { it.id }) { item -> ItemRow(item, onClick = { onItem(item) }) }
        }
    }
}

@Composable
private fun MorePage(onApps: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Manage installed apps, permissions and how VaultOS works on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onApps),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Android, null, tint = Color(0xFF9AE6C1), modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Installed apps & APKs", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Compare installed versions and archive app package components.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onSettings),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFF8DBBFF), modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Settings & permissions", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Storage access, capture keyboard, backup and device integration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Metric(label: String, value: String) {
    Column { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray) }
}

@Composable private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: () -> Unit) {
    Card(modifier.clickable(onClick = action), colors = CardDefaults.cardColors(containerColor = Color(0xFF171D24)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = Color(0xFF9AE6C1)); Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun ItemList(items: List<VaultItem>, onItem: (VaultItem) -> Unit, onPin: (VaultItem, Boolean) -> Unit) {
    if (items.isEmpty()) EmptyState("Nothing here yet", "Incoming shares and imported files will appear here.")
    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(items, key = { it.id }) { item -> ItemRow(item, onClick = { onItem(item) }, onPin = { onPin(item, !item.isPinned) }) }
    }
}

@Composable private fun ItemRow(item: VaultItem, onClick: () -> Unit, onPin: (() -> Unit)? = null) {
    val icon = when (item.category) {
        "Images" -> Icons.Default.Image
        "Videos" -> Icons.Default.Movie
        "Apps & APKs" -> Icons.Default.Android
        "Text" -> Icons.Default.Notes
        "Code" -> Icons.Default.Code
        "Archives" -> Icons.Default.FolderZip
        "Audio" -> Icons.Default.AudioFile
        else -> Icons.Default.Description
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(42.dp).background(Color(0xFF222C35), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { FileThumbnail(item) }
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("${item.category} · ${formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.isPinned) Icon(Icons.Default.PushPin, "Pinned", tint = Color(0xFF9AE6C1))
            else Icon(if (item.isArchived) Icons.Default.Inventory2 else Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            onPin?.let { IconButton(onClick = it) { Icon(if (item.isPinned) Icons.Default.PushPin else Icons.Default.PushPin, "Pin item", tint = if (item.isPinned) Color(0xFF9AE6C1) else MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun CollectionsPage(
    dao: VaultDao,
    items: List<VaultItem>,
    onCategory: (String) -> Unit,
    onTag: (String) -> Unit,
    onSavedSearch: (String) -> Unit,
    onItem: (VaultItem) -> Unit,
    onMessage: (String) -> Unit
) {
    val collections by dao.observeCollections().collectAsState(initial = emptyList())
    val tags by dao.observeTags().collectAsState(initial = emptyList())
    val savedSearches by dao.observeSavedSearches().collectAsState(initial = emptyList())
    val duplicateGroups by dao.observeDuplicateGroups().collectAsState(initial = emptyList())
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    var selectedDuplicateHash by remember { mutableStateOf<String?>(null) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var newCollectionDescription by remember { mutableStateOf("") }
    val collectionItems by remember(selectedCollectionId) {
        selectedCollectionId?.let { dao.observeCollectionItems(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val duplicateItems by remember(selectedDuplicateHash) {
        selectedDuplicateHash?.let { dao.observeDuplicateItems(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        when {
            selectedCollectionId != null -> {
                val selected = collections.firstOrNull { it.id == selectedCollectionId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedCollectionId = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(selected?.name ?: "Collection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${collectionItems.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (collectionItems.isEmpty()) EmptyState("Collection is empty", "Open an item and add it to this collection.")
                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    items(collectionItems, key = { it.id }) { item -> ItemRow(item, onClick = { onItem(item) }) }
                }
            }
            selectedDuplicateHash != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedDuplicateHash = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Matching files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("SHA-256 ${selectedDuplicateHash?.take(16)}…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    items(duplicateItems, key = { it.id }) { item -> ItemRow(item, onClick = { onItem(item) }) }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your library, organized", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Automatic groups, your own collections and tags", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { showCreateCollection = true }) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("New") }
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                    item { Text("SMART CATEGORIES", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium) }
                    val groups = items.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
                    if (groups.isEmpty()) item { Text("No indexed items yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(groups, key = { "category-${it.first}" }) { (name, total) ->
                        Card(Modifier.fillMaxWidth().clickable { onCategory(name) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = Color(0xFF9AE6C1)); Text(name, Modifier.weight(1f).padding(start = 12.dp)); Text(total.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("YOUR COLLECTIONS", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium) }
                    if (collections.isEmpty()) item { Text("Create a collection, then add items from their detail view.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(collections, key = { "collection-${it.id}" }) { collection ->
                        Card(Modifier.fillMaxWidth().clickable { selectedCollectionId = collection.id }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CollectionsBookmark, null, tint = Color(0xFF8DBBFF)); Text(collection.name, Modifier.weight(1f).padding(start = 12.dp)); IconButton(onClick = { scope.launch { dao.deleteCollection(collection.id); onMessage("Collection deleted") } }) { Icon(Icons.Default.DeleteOutline, "Delete collection") }; Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("TAGS", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium) }
                    if (tags.isEmpty()) item { Text("Add tags from an item’s detail view.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(tags, key = { "tag-${it.id}" }) { tag ->
                        Card(Modifier.fillMaxWidth().clickable { onTag(tag.name) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Tag, null); Text(tag.name, Modifier.weight(1f).padding(start = 10.dp)); IconButton(onClick = { scope.launch { dao.deleteTag(tag.id); onMessage("Tag deleted") } }) { Icon(Icons.Default.DeleteOutline, "Delete tag") }; Icon(Icons.Default.ChevronRight, null) }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("SAVED SEARCHES", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium) }
                    if (savedSearches.isEmpty()) item { Text("Save a search from the search bar to keep it here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(savedSearches, key = { "search-${it.id}" }) { saved ->
                        Card(Modifier.fillMaxWidth().clickable { onSavedSearch(saved.query) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bookmark, null); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(saved.name); Text(saved.query, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { scope.launch { dao.deleteSavedSearch(saved.id); onMessage("Saved search deleted") } }) { Icon(Icons.Default.DeleteOutline, "Delete saved search") }; Icon(Icons.Default.ChevronRight, null) }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); Text("DUPLICATE GROUPS", color = Color(0xFF9AE6C1), style = MaterialTheme.typography.labelMedium) }
                    if (duplicateGroups.isEmpty()) item { Text("No duplicate content groups found in the indexed library.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(duplicateGroups, key = { "duplicate-${it.hash}" }) { group ->
                        Card(Modifier.fillMaxWidth().clickable { selectedDuplicateHash = group.hash }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFFFC477)); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("${group.itemCount} identical files"); Text("${group.hash.take(16)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(formatBytes(group.totalBytes))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateCollection) {
        AlertDialog(
            onDismissRequest = { showCreateCollection = false },
            title = { Text("New collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newCollectionName, { newCollectionName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(newCollectionDescription, { newCollectionDescription = it }, label = { Text("Description (optional)") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = normalizeLibraryLabel(newCollectionName)
                    if (name == null) onMessage("Enter a collection name")
                    else scope.launch {
                        val now = System.currentTimeMillis()
                        dao.insertCollection(UserCollection(UUID.randomUUID().toString(), name.first, name.second, newCollectionDescription.trim().take(240), now, now))
                        val saved = dao.findCollection(name.second)
                        if (saved != null) { selectedCollectionId = saved.id; onMessage("Collection created") }
                        showCreateCollection = false; newCollectionName = ""; newCollectionDescription = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateCollection = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ItemOrganizerSection(dao: VaultDao, item: VaultItem, scope: CoroutineScope) {
    val tags by dao.observeTags().collectAsState(initial = emptyList())
    val assignedTags by dao.observeTagsForItem(item.id).collectAsState(initial = emptyList())
    val collections by dao.observeCollections().collectAsState(initial = emptyList())
    val assignedCollections by dao.observeCollectionsForItem(item.id).collectAsState(initial = emptyList())
    var newTag by remember(item.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TAGS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9AE6C1))
        if (tags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tags, key = { it.id }) { tag ->
                FilterChip(
                    selected = assignedTags.any { it.id == tag.id },
                    onClick = { scope.launch { if (assignedTags.any { it.id == tag.id }) dao.removeItemTag(item.id, tag.id) else dao.addItemTag(ItemTagCrossRef(item.id, tag.id, System.currentTimeMillis())) } },
                    label = { Text(tag.name) }, leadingIcon = { Icon(Icons.Default.Tag, null) }
                )
            }
        } else Text("No tags yet. Create one below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newTag, { newTag = it }, modifier = Modifier.weight(1f), label = { Text("Create or add tag") }, singleLine = true)
            IconButton(onClick = {
                val normalized = normalizeLibraryLabel(newTag)
                if (normalized != null) scope.launch {
                    dao.insertTag(UserTag(UUID.randomUUID().toString(), normalized.first, normalized.second, System.currentTimeMillis()))
                    val tag = dao.findTag(normalized.second)
                    if (tag != null) dao.addItemTag(ItemTagCrossRef(item.id, tag.id, System.currentTimeMillis()))
                    newTag = ""
                }
            }) { Icon(Icons.Default.AddCircle, "Add tag") }
        }
        Text("COLLECTIONS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9AE6C1))
        if (collections.isEmpty()) Text("Create a collection in the Collections tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(collections, key = { it.id }) { collection ->
                FilterChip(
                    selected = assignedCollections.any { it.id == collection.id },
                    onClick = { scope.launch { if (assignedCollections.any { it.id == collection.id }) dao.removeCollectionItem(collection.id, item.id) else dao.addCollectionItem(CollectionItemCrossRef(collection.id, item.id, System.currentTimeMillis())) } },
                    label = { Text(collection.name) }, leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) }
                )
            }
        }
    }
}

@Composable
private fun ItemMetadataSection(
    context: Context,
    dao: VaultDao,
    item: VaultItem,
    scope: CoroutineScope,
    onMessage: (String) -> Unit
) {
    val metadata by dao.observeMetadata(item.id).collectAsState(initial = null)
    var extracting by remember(item.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("LOCAL METADATA", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9AE6C1))
        metadata?.let { meta ->
            meta.title?.let { Text("Title: $it", style = MaterialTheme.typography.bodySmall) }
            meta.author?.let { Text("By: $it", style = MaterialTheme.typography.bodySmall) }
            meta.subject?.let { Text("Subject: $it", style = MaterialTheme.typography.bodySmall) }
            if (meta.pageCount != null) Text("Pages: ${meta.pageCount}", style = MaterialTheme.typography.bodySmall)
            if (meta.widthPx != null && meta.heightPx != null) Text("Dimensions: ${meta.widthPx} × ${meta.heightPx}", style = MaterialTheme.typography.bodySmall)
            if (meta.durationMs != null) Text("Duration: ${meta.durationMs / 1000}s", style = MaterialTheme.typography.bodySmall)
            meta.capturedAt?.let { Text("Captured: $it", style = MaterialTheme.typography.bodySmall) }
            meta.cameraModel?.let { Text("Camera: $it", style = MaterialTheme.typography.bodySmall) }
            meta.packageName?.let { Text("Package: $it", style = MaterialTheme.typography.bodySmall) }
            meta.packageVersionName?.let { Text("Version: $it (${meta.packageVersionCode ?: 0})", style = MaterialTheme.typography.bodySmall) }
        } ?: Text("Metadata is read only when you request it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = !extracting, onClick = {
            extracting = true
            scope.launch {
                runCatching { MetadataExtractor.extract(context, item) }
                    .onSuccess { dao.upsertMetadata(it); onMessage("Metadata extracted on this device") }
                    .onFailure { onMessage(it.message ?: "Metadata extraction failed") }
                extracting = false
            }
        }) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text(if (extracting) "Reading metadata…" else if (metadata == null) "Extract metadata" else "Refresh metadata") }
    }
}

@Composable
private fun ItemRelatedSection(dao: VaultDao, item: VaultItem, onItem: (VaultItem) -> Unit) {
    val related by dao.observeRelatedItems(item.id).collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("RELATED ITEMS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9AE6C1))
        Text("Files with the same category, tags or collection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (related.isEmpty()) Text("No related files found yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else related.forEach { candidate ->
            TextButton(onClick = { onItem(candidate) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun normalizeLibraryLabel(raw: String): Pair<String, String>? {
    val name = raw.trim().replace(Regex("\\s+"), " ").take(48)
    if (name.isBlank()) return null
    return name to name.lowercase(Locale.ROOT)
}

@Composable private fun FilesPage(
    activity: MainActivity, repository: VaultRepository, indexed: List<VaultItem>, trashItems: List<VaultItem>,
    permissionGranted: Boolean, onItem: (VaultItem) -> Unit, onPermission: () -> Unit, onMessage: (String) -> Unit
) {
    var mode by remember { mutableStateOf("Library") }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            FilterChip(selected = mode == "Library", onClick = { mode = "Library" }, label = { Text("Library") }, leadingIcon = { Icon(Icons.Default.Inventory2, null) })
            FilterChip(selected = mode == "Folders", onClick = { mode = "Folders" }, label = { Text("Device folders") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
            FilterChip(selected = mode == "Trash", onClick = { mode = "Trash" }, label = { Text("Recycle Bin") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
        }
        when (mode) {
            "Folders" -> FileBrowserPage(activity, repository, permissionGranted, onPermission, onMessage)
            "Trash" -> TrashPage(activity, repository, trashItems, onMessage)
            else -> ItemList(indexed, onItem = onItem, onPin = { item, pinned -> activity.lifecycleScope.launch { repository.setPinned(item, pinned) } })
        }
    }
}

@Composable
private fun TrashPage(activity: MainActivity, repository: VaultRepository, items: List<VaultItem>, onMessage: (String) -> Unit) {
    var permanentTarget by remember { mutableStateOf<VaultItem?>(null) }
    if (items.isEmpty()) EmptyState("Recycle Bin is empty", "Archived files you delete from the VaultOS library can be restored here.")
    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        items(items, key = { it.id }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    ItemRow(item.copy(isArchived = true), onClick = { onMessage(item.name + " is in the Recycle Bin") })
                    Text("In Recycle Bin · " + formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            activity.lifecycleScope.launch {
                                runCatching { repository.restoreFromTrash(item) }
                                    .onSuccess { onMessage(if (it) "Restored " + item.name else "Original file was unavailable") }
                                    .onFailure { onMessage(it.message ?: "Restore failed") }
                            }
                        }) { Text("Restore") }
                        TextButton(onClick = { permanentTarget = item }) { Text("Delete permanently") }
                    }
                }
            }
        }
    }
    permanentTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { permanentTarget = null },
            title = { Text("Delete " + item.name + " permanently?") },
            text = { Text("This removes the archived file and its library record. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    activity.lifecycleScope.launch {
                        runCatching { repository.permanentlyDeleteTrashedItem(item) }
                            .onSuccess { onMessage(if (it) "Permanently deleted " + item.name else "Could not delete item") }
                            .onFailure { onMessage(it.message ?: "Permanent delete failed") }
                    }
                    permanentTarget = null
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { permanentTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FileBrowserPage(activity: MainActivity, repository: VaultRepository, permissionGranted: Boolean, onPermission: () -> Unit, onMessage: (String) -> Unit) {
    var vaultMode by remember { mutableStateOf(false) }
    var current by remember(vaultMode) { mutableStateOf(if (vaultMode) repository.root else Environment.getExternalStorageDirectory()) }
    var refresh by remember { mutableIntStateOf(0) }
    var menuFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var batchDeleteTarget by remember { mutableStateOf<List<File>?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var dialogName by remember { mutableStateOf("") }
    var queuedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var queuedCut by remember { mutableStateOf(false) }
    var selectedPaths by remember(current.absolutePath) { mutableStateOf(emptySet<String>()) }
    var operationProgress by remember { mutableStateOf<FileOperationProgress?>(null) }
    var operationLabel by remember { mutableStateOf("") }
    var operationJob by remember { mutableStateOf<Job?>(null) }

    fun isInsideVault(file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(repository.root.canonicalFile.toPath())
    }.getOrDefault(false)

    fun scheduleVaultSync() {
        val request = OneTimeWorkRequestBuilder<StorageScanWorker>()
            .setInputData(androidx.work.workDataOf(StorageScanWorker.KEY_VAULT_ONLY to true)).build()
        WorkManager.getInstance(activity).enqueueUniqueWork("vaultos-vault-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun uniqueTarget(parent: File, name: String): File {
        var candidate = File(parent, name)
        var index = 1
        while (candidate.exists()) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0 && !name.startsWith(".")) File(parent, name.substring(0, dot) + "_" + index + name.substring(dot)) else File(parent, name + "_" + index)
            index++
        }
        return candidate
    }

    fun runOperation(label: String, block: suspend ((FileOperationProgress) -> Unit) -> Unit) {
        if (operationJob?.isActive == true) return
        operationLabel = label
        operationProgress = FileOperationProgress(0, 0, "Preparing…", 0, 0)
        operationJob = activity.lifecycleScope.launch {
            try {
                block { update -> operationProgress = update }
                operationProgress = null
                operationJob = null
                refresh++
                onMessage(label + " complete")
            } catch (cancelled: CancellationException) {
                operationProgress = null
                operationJob = null
                refresh++
                onMessage(label + " cancelled")
            } catch (failure: Throwable) {
                operationProgress = null
                operationJob = null
                refresh++
                onMessage(failure.message ?: (label + " failed"))
            }
        }
    }

    fun archiveFiles(files: List<File>) {
        val sources = files.filter { !isInsideVault(it) }
        if (sources.isEmpty()) { onMessage("Selected items are already in the Vault archive"); return }
        runOperation("Archive " + sources.size + " item(s)") { progress ->
            val imports = File(repository.root, "Imports").apply { mkdirs() }
            sources.forEach { source -> FileOperations.copy(source, uniqueTarget(imports, source.name), progress) }
            repository.syncVaultContents()
        }
    }

    fun pasteQueued() {
        val sources = queuedFiles
        if (sources.isEmpty()) return
        val destinationFolder = current
        if (sources.any { source -> source.canonicalFile == destinationFolder.canonicalFile ||
            (source.isDirectory && destinationFolder.canonicalPath.startsWith(source.canonicalPath + File.separator)) }) {
            onMessage("Cannot paste a folder into itself"); return
        }
        val cut = queuedCut
        val touchesVault = sources.any(::isInsideVault) || isInsideVault(destinationFolder)
        runOperation((if (cut) "Move " else "Copy ") + sources.size + " item(s)") { progress ->
            sources.forEach { source ->
                val destination = uniqueTarget(destinationFolder, source.name)
                if (cut) FileOperations.move(source, destination, progress)
                else FileOperations.copy(source, destination, progress)
            }
            if (touchesVault) repository.syncVaultContents()
        }
        queuedFiles = emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            FilterChip(selected = !vaultMode, onClick = { vaultMode = false; selectedPaths = emptySet() }, label = { Text("Phone storage") })
            FilterChip(selected = vaultMode, onClick = { vaultMode = true; selectedPaths = emptySet() }, label = { Text("Vault archive") })
        }
        if (!vaultMode && !permissionGranted) {
            EmptyState("Allow file access", "Android requires explicit all-files access for full device browsing and background indexing.")
            Button(onClick = onPermission, modifier = Modifier.fillMaxWidth()) { Text("Open storage permission") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                IconButton(onClick = {
                    val base = if (vaultMode) repository.root else Environment.getExternalStorageDirectory()
                    if (current.canonicalFile != base.canonicalFile) current = current.parentFile ?: base
                }) { Icon(Icons.Default.ArrowUpward, "Parent folder") }
                Text(current.absolutePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath).ifBlank { if (vaultMode) "/VaultOS" else "/" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                if (queuedFiles.isNotEmpty()) TextButton(onClick = { pasteQueued() }) { Text(if (queuedCut) "Move here" else "Paste here") }
                IconButton(onClick = { dialogName = ""; showNewFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
            }
            if (selectedPaths.isNotEmpty()) {
                val selectedFiles = selectedPaths.mapNotNull { path -> File(path).takeIf { it.exists() } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedFiles.size.toString() + " selected", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { queuedFiles = selectedFiles; queuedCut = false; selectedPaths = emptySet(); onMessage("Choose a destination and tap Paste here") }) { Text("Copy") }
                    TextButton(onClick = { queuedFiles = selectedFiles; queuedCut = true; selectedPaths = emptySet(); onMessage("Choose a destination and tap Move here") }) { Text("Move") }
                    TextButton(onClick = {
                        runOperation("Create ZIP") { progress ->
                            FileOperations.createZip(selectedFiles, uniqueTarget(current, "VaultOS-" + System.currentTimeMillis() + ".zip"), progress)
                            if (vaultMode) repository.syncVaultContents()
                        }
                        selectedPaths = emptySet()
                    }) { Text("ZIP") }
                    TextButton(onClick = { archiveFiles(selectedFiles); selectedPaths = emptySet() }) { Text("Archive") }
                    TextButton(onClick = { batchDeleteTarget = selectedFiles }) { Text("Delete") }
                    TextButton(onClick = { selectedPaths = emptySet() }) { Text("Clear") }
                }
            }
            operationProgress?.let { progress ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18251F))) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(operationLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { operationJob?.cancel() }) { Text("Cancel") }
                        }
                        LinearProgressIndicator(progress = progress.fraction, modifier = Modifier.fillMaxWidth())
                        Text(formatBytes(progress.completedBytes) + " / " + formatBytes(progress.totalBytes) + " · " +
                            progress.completedFiles + "/" + progress.totalFiles + " files · " + progress.currentFile,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            var children by remember(current.absolutePath) { mutableStateOf(emptyList<File>()) }
            LaunchedEffect(current.absolutePath, refresh) {
                val directory = current
                children = withContext(Dispatchers.IO) {
                    directory.listFiles()?.filter { it.canRead() && !it.name.startsWith(".") }
                        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty()
                }
            }
            if (children.isEmpty()) EmptyState("Folder is empty", "New files detected in shared storage are indexed and incoming files are archived.")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(children, key = { it.absolutePath }) { file ->
                    val selected = file.absolutePath in selectedPaths
                    Card(Modifier.fillMaxWidth().clickable {
                        if (selectedPaths.isNotEmpty()) selectedPaths = if (selected) selectedPaths - file.absolutePath else selectedPaths + file.absolutePath
                        else if (file.isDirectory) current = file else openDeviceFile(activity, file, onMessage)
                    }, colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF20372E) else Color(0xFF151A20))) {
                        Row(Modifier.padding(start = 7.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = { checked -> selectedPaths = if (checked) selectedPaths + file.absolutePath else selectedPaths - file.absolutePath })
                            Icon(if (file.isDirectory) Icons.Default.Folder else if (file.extension.equals("zip", true)) Icons.Default.FolderZip else Icons.Default.InsertDriveFile, null, tint = Color(0xFF9AE6C1))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (file.isDirectory) "Folder" else formatBytes(file.length()) + " · " + file.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (file.isFile) IconButton(onClick = { archiveFiles(listOf(file)) }) { Icon(Icons.Default.Archive, "Archive a copy") }
                            Box {
                                IconButton(onClick = { menuFile = file }) { Icon(Icons.Default.MoreVert, "File actions") }
                                DropdownMenu(expanded = menuFile == file, onDismissRequest = { menuFile = null }) {
                                    if (file.isFile) DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuFile = null; shareDeviceFile(activity, file, onMessage) })
                                    DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { queuedFiles = listOf(file); queuedCut = false; menuFile = null; onMessage(file.name + " ready to paste") })
                                    DropdownMenuItem(text = { Text("Cut") }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = { queuedFiles = listOf(file); queuedCut = true; menuFile = null; onMessage(file.name + " ready to move") })
                                    if (file.isFile && file.extension.equals("zip", true)) DropdownMenuItem(text = { Text("Extract here") }, leadingIcon = { Icon(Icons.Default.Unarchive, null) }, onClick = {
                                        menuFile = null
                                        runOperation("Extract " + file.name) { progress ->
                                            FileOperations.extractZip(file, uniqueTarget(current, file.nameWithoutExtension.ifBlank { "Extracted" }), progress)
                                            if (vaultMode) repository.syncVaultContents()
                                        }
                                    })
                                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { renameTarget = file; dialogName = file.name; menuFile = null })
                                    DropdownMenuItem(text = { Text(if (vaultMode) "Move to Recycle Bin" else "Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { deleteTarget = file; menuFile = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) AlertDialog(
        onDismissRequest = { showNewFolder = false }, title = { Text("Create folder") },
        text = { OutlinedTextField(dialogName, { dialogName = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val safe = dialogName.trim().replace(Regex("[/\\\\\\u0000-\\u001f]"), "_").take(120)
            val created = safe.isNotBlank() && File(current, safe).mkdir()
            if (created && vaultMode) scheduleVaultSync()
            showNewFolder = false; refresh++
            onMessage(if (created) "Folder created" else "Could not create folder")
        }) { Text("Create") } }, dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
    )
    renameTarget?.let { target -> AlertDialog(
        onDismissRequest = { renameTarget = null }, title = { Text("Rename") },
        text = { OutlinedTextField(dialogName, { dialogName = it }, label = { Text("New name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val safe = dialogName.trim().replace(Regex("[/\\\\\\u0000-\\u001f]"), "_").take(180)
            val destination = File(target.parentFile, safe)
            val ok = safe.isNotBlank() && !destination.exists() && target.renameTo(destination)
            if (ok && vaultMode) scheduleVaultSync()
            renameTarget = null; refresh++
            onMessage(if (ok) "Renamed to " + safe else "Could not rename file")
        }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
    ) }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text(if (vaultMode) "Move " + target.name + " to Recycle Bin?" else "Delete " + target.name + "?") },
            text = { Text(if (vaultMode) "The managed archive file will be moved to the Recycle Bin and can be restored." else "This permanently deletes the selected file or folder from its original location.") },
            confirmButton = { TextButton(onClick = {
                val targetFile = target
                deleteTarget = null
                if (vaultMode && targetFile.isFile) {
                    activity.lifecycleScope.launch {
                        runCatching { repository.trashArchivedFile(targetFile) }
                            .onSuccess { onMessage(if (it) "Moved to Recycle Bin" else "This file is not a managed archive item") }
                            .onFailure { onMessage(it.message ?: "Could not move to Recycle Bin") }
                        refresh++
                    }
                } else if (vaultMode) onMessage("Trash individual archived files; folders are not trashed as a group")
                else runOperation("Delete " + targetFile.name) { progress -> FileOperations.delete(targetFile, progress) }
            }) { Text(if (vaultMode) "Move to trash" else "Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    batchDeleteTarget?.let { selected ->
        AlertDialog(
            onDismissRequest = { batchDeleteTarget = null },
            title = { Text("Delete " + selected.size + " selected item(s)?") },
            text = { Text(if (vaultMode) "Selected managed files will move to the Recycle Bin. Folders are skipped." else "Selected files and folders will be permanently deleted from their original locations.") },
            confirmButton = { TextButton(onClick = {
                batchDeleteTarget = null
                runOperation(if (vaultMode) "Move to Recycle Bin" else "Delete selected items") { progress ->
                    if (vaultMode) selected.filter { it.isFile }.forEach { repository.trashArchivedFile(it) }
                    else selected.forEach { FileOperations.delete(it, progress) }
                    if (vaultMode) repository.syncVaultContents()
                }
                selectedPaths = emptySet()
            }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { batchDeleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ItemPreview(item: VaultItem, maxHeight: Int) {
    val path = item.archivedPath ?: item.originalUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = null
        if (!path.isNullOrBlank()) bitmap = withContext(Dispatchers.IO) { decodeVaultThumbnail(File(path), 640) }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight.dp), contentScale = ContentScale.Fit)
}

@Composable
private fun FileThumbnail(item: VaultItem) {
    val path = item.archivedPath ?: item.originalUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = null
        if (!path.isNullOrBlank()) bitmap = withContext(Dispatchers.IO) { decodeVaultThumbnail(File(path), 192) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        return
    }
    val icon = when (item.category) {
        "Images" -> Icons.Default.Image
        "Videos" -> Icons.Default.Movie
        "Apps & APKs" -> Icons.Default.Android
        "Text" -> Icons.Default.Notes
        "Code" -> Icons.Default.Code
        "Archives" -> Icons.Default.FolderZip
        "Audio" -> Icons.Default.AudioFile
        else -> Icons.Default.Description
    }
    Icon(icon, null, tint = Color(0xFF9AE6C1))
}

private val thumbnailCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024L / 12L).toInt().coerceAtLeast(1024)) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

private fun decodeVaultThumbnail(file: File, maxDimension: Int): Bitmap? {
    if (!file.isFile || !file.canRead()) return null
    val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}:$maxDimension"
    thumbnailCache.get(key)?.let { return it }
    val decoded = runCatching {
        when (file.extension.lowercase()) {
            "pdf" -> decodePdfFirstPage(file, maxDimension)
            "mp4", "m4v", "mov", "3gp", "mkv", "webm" -> decodeVideoFrame(file)
            "mp3", "m4a", "aac", "flac", "ogg", "wav" -> decodeAudioArtwork(file, maxDimension)
            else -> decodeSampledImage(file, maxDimension)
        }
    }.getOrNull()
    if (decoded != null) thumbnailCache.put(key, decoded)
    return decoded
}

private fun decodeSampledImage(file: File, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    })
}

private fun decodePdfFirstPage(file: File, maxDimension: Int): Bitmap? {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfRenderer(descriptor).use { renderer ->
        if (renderer.pageCount == 0) return null
        val page = renderer.openPage(0)
        try {
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(page.width, page.height).toFloat())
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }
}

private fun decodeVideoFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        retriever.release()
    }
}

private fun decodeAudioArtwork(file: File, maxDimension: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    val bytes = try {
        retriever.setDataSource(file.absolutePath)
        retriever.embeddedPicture
    } finally {
        retriever.release()
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    })
}

private data class AppCatalogueEntry(
    val application: android.content.pm.ApplicationInfo,
    val packageName: String,
    val label: String,
    val installedVersion: String,
    val installedVersionCode: Long,
    val splitCount: Int,
    val archivedVersion: String?,
    val archivedVersionCode: Long?,
    val archivedComponentCount: Int
)

private fun packageVersionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

@Suppress("DEPRECATION")
private fun loadAppCatalogue(activity: MainActivity, archivedItems: List<VaultItem>): List<AppCatalogueEntry> {
    val pm = activity.packageManager
    val archivedByPackage = mutableMapOf<String, MutableList<PackageInfo>>()
    archivedItems.asSequence().filter { it.archivedPath?.endsWith(".apk", true) == true }.forEach { item ->
        val path = item.archivedPath ?: return@forEach
        val archived = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0L))
            else pm.getPackageArchiveInfo(path, 0)
        }.getOrNull() ?: return@forEach
        val packageName = archived.packageName ?: return@forEach
        archivedByPackage.getOrPut(packageName) { mutableListOf() }.add(archived)
    }
    return pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { app ->
        runCatching {
            val installed = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
                else pm.getPackageInfo(app.packageName, 0)
            val archived = archivedByPackage[app.packageName].orEmpty().maxByOrNull(::packageVersionCode)
            val installedCode = packageVersionCode(installed)
            val archivedCode = archived?.let(::packageVersionCode)
            AppCatalogueEntry(
                application = app,
                packageName = app.packageName,
                label = pm.getApplicationLabel(app).toString(),
                installedVersion = installed.versionName?.takeIf { it.isNotBlank() } ?: installedCode.toString(),
                installedVersionCode = installedCode,
                splitCount = app.splitSourceDirs?.size ?: 0,
                archivedVersion = archived?.let { it.versionName?.takeIf(String::isNotBlank) ?: packageVersionCode(it).toString() },
                archivedVersionCode = archivedCode,
                archivedComponentCount = if (archivedCode == null) 0 else archivedByPackage[app.packageName].orEmpty().count { packageVersionCode(it) == archivedCode }
            )
        }.getOrNull()
    }.sortedBy { it.label.lowercase() }
}

@Composable private fun AppsPage(activity: MainActivity, repository: VaultRepository, onMessage: (String) -> Unit) {
    val archivedItems by remember { VaultDatabase.get(activity).dao().observeCategory("Apps & APKs") }.collectAsState(initial = emptyList())
    var apps by remember { mutableStateOf<List<AppCatalogueEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(archivedItems.map { it.id to it.modifiedAt }) {
        loading = true
        apps = withContext(Dispatchers.IO) { runCatching { loadAppCatalogue(activity, archivedItems) }.getOrDefault(emptyList()) }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("Installed applications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${apps.size} visible packages · Compare installed versions with APKs archived in VaultOS. Split APK sets are exported component by component.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { entry ->
                val comparison = when {
                    entry.archivedVersionCode == null -> "No archived APK"
                    entry.archivedVersionCode < entry.installedVersionCode -> "Installed version is newer"
                    entry.archivedVersionCode > entry.installedVersionCode -> "Archived version is newer"
                    else -> "Versions match"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, tint = Color(0xFF9AE6C1), modifier = Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Installed ${entry.installedVersion} · Vault ${entry.archivedVersion ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Text("$comparison · ${entry.splitCount} installed split(s) · ${entry.archivedComponentCount} archived component(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))) }) { Icon(Icons.Default.Info, "App details") }
                    IconButton(onClick = {
                        activity.lifecycleScope.launch {
                            val packagePaths = buildList {
                                entry.application.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
                                entry.application.splitSourceDirs?.filter(String::isNotBlank)?.let(::addAll)
                            }.distinct()
                            if (packagePaths.isEmpty()) onMessage("APK source is unavailable")
                            else {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        packagePaths.forEach { path ->
                                            repository.archiveIncomingFile(File(path), "Installed application package · ${entry.packageName}", force = true)
                                                ?: throw IllegalStateException("Could not read APK component $path")
                                        }
                                    }
                                }
                                result.onSuccess { onMessage("Archived ${packagePaths.size} APK component(s) for ${entry.label}. Keep the base APK and every split together when reinstalling.") }
                                    .onFailure { onMessage(it.message ?: "APK export failed") }
                            }
                        }
                    }) { Icon(Icons.Default.Archive, "Archive base and split APKs") }
                }
                Divider(color = Color(0xFF252C34))
            }
        }
    }
}

@Composable private fun SettingsPage(activity: MainActivity, repository: VaultRepository, permissionGranted: Boolean, backupLauncher: androidx.activity.result.ActivityResultLauncher<Uri?>, onMessage: (String) -> Unit) {
    val prefs = activity.getSharedPreferences(VaultKeyboardService.PREFS, Context.MODE_PRIVATE)
    var autoCapture by remember { mutableStateOf(prefs.getBoolean(VaultKeyboardService.KEY_AUTO_CAPTURE, true)) }
    var keyboardLanguage by remember { mutableStateOf(prefs.getString(VaultKeyboardService.KEY_LANGUAGE, "en") ?: "en") }
    var busy by remember { mutableStateOf(false) }
    val backupTree = prefs.getString("backup_tree", null)
    val usbTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            busy = true
            activity.lifecycleScope.launch {
                val result = runCatching {
                    com.vaultos.app.storage.UsbOtgImporter.importTree(activity, repository, treeUri)
                }
                busy = false
                result.onSuccess {
                    val suffix = if (it.truncated) " · stopped at the 2,000-file safety limit" else ""
                    onMessage("USB import complete · ${it.imported} imported · ${it.failed} failed$suffix")
                }.onFailure { onMessage(it.message ?: "USB import failed") }
            }
        }
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings & permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device storage access", fontWeight = FontWeight.SemiBold)
                Text(if (permissionGranted) "Full shared-storage access is enabled. VaultOS can index and monitor readable shared folders." else "Grant all-files access to enable device-wide folder browsing and automatic indexing. Android still protects other apps' private data folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { openStorageSettings(activity) }) { Text(if (permissionGranted) "Review permission" else "Grant all-files access") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("APK installation", fontWeight = FontWeight.SemiBold)
                Text("VaultOS can archive APK packages. Android still controls installation and asks you to approve apps from this source.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    }
                }) { Text("Manage install permission") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Capture keyboard", fontWeight = FontWeight.SemiBold)
                Text("Enable and select VaultOS Capture Keyboard in Android settings. While it is active, clipboard changes can be captured automatically. Gboard cannot be monitored by a separate background app; switch back to Gboard at any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Keyboard language", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "pl" to "Polski", "tr" to "Türkçe").forEach { (code, label) ->
                        FilterChip(
                            selected = keyboardLanguage == code,
                            onClick = {
                                keyboardLanguage = code
                                prefs.edit().putString(VaultKeyboardService.KEY_LANGUAGE, code).apply()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Type includes EN / PL / TR completions, typo corrections, opt-in local word learning, personal dictionary, and next-word suggestions. Double-space punctuation and sentence capitalization are built in. Clips searches clipboard history; Cmd finds VaultOS content. More contains keyboard controls and privacy settings.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Automatically save clipboard content"); Text("On when VaultOS is the active keyboard. Captures text and shared clipboard attachments; sensitive clips and password fields are skipped when detectable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "Automatically save clipboard content" },
                        checked = autoCapture,
                        onCheckedChange = { autoCapture = it; prefs.edit().putBoolean(VaultKeyboardService.KEY_AUTO_CAPTURE, it).apply() }
                    )
                }
                OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) { Text("Manage keyboards") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Xiaomi background settings", fontWeight = FontWeight.SemiBold)
                Text(
                    "MIUI and HyperOS may stop background work to save battery. If new downloads are not picked up while VaultOS is closed, review these settings. Menu names vary by system version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("1. Open VaultOS app info. Enable Autostart if your device shows it, then set Battery saver to No restrictions.", style = MaterialTheme.typography.bodySmall)
                Text("2. In Recent apps, you can lock VaultOS if your system offers that option. This is optional and may use more battery.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { openAppDetailsSettings(activity) }) { Text("Open app info") }
                    OutlinedButton(onClick = { openBatteryOptimizationSettings(activity) }) { Text("Battery settings") }
                }
                Text(
                    "These are Android settings shortcuts, not a guarantee that Xiaomi will keep the app alive. WorkManager remains best-effort and is not an exact timer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("External backup", fontWeight = FontWeight.SemiBold)
                Text(if (backupTree == null) "Choose an external folder, USB drive or another SAF-supported destination." else "Backup destination configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupLauncher.launch(null) }) { Text("Choose folder") }
                    Button(enabled = backupTree != null && !busy, onClick = {
                        busy = true
                        activity.lifecycleScope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { VaultBackupManager.backup(activity, Uri.parse(backupTree!!)) } }
                            busy = false
                            result.onSuccess { onMessage("Backup complete · ${it.files} files · ${it.records} records · ${formatBytes(it.bytes)}") }
                                .onFailure { onMessage(it.message ?: "Backup failed") }
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Back up now") }
                    OutlinedButton(enabled = backupTree != null && !busy, onClick = {
                        busy = true
                        activity.lifecycleScope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { VaultBackupManager.restore(activity, Uri.parse(backupTree!!)) } }
                            busy = false
                            result.onSuccess {
                                val request = OneTimeWorkRequestBuilder<StorageScanWorker>()
                                    .setInputData(androidx.work.workDataOf(StorageScanWorker.KEY_VAULT_ONLY to true))
                                    .build()
                                WorkManager.getInstance(activity).enqueueUniqueWork("vaultos-vault-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                                onMessage("Restore complete · ${it.records} records · ${it.files} files")
                            }.onFailure { onMessage(it.message ?: "Restore failed") }
                        }
                    }) { Text("Restore backup") }
                }
            }
        }
        var usbRefresh by remember { mutableIntStateOf(0) }
        val usbHostSupported = remember(activity) { UsbOtgDiagnostics.hostSupported(activity) }
        val usbDevices = remember(usbRefresh, activity.resumeRevision) { UsbOtgDiagnostics.attachedDevices(activity) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("USB / OTG diagnostics", fontWeight = FontWeight.SemiBold)
                Text(
                    if (!usbHostSupported) "This Android device does not advertise USB Host support. USB flash-drive access through OTG may not be available."
                    else if (usbDevices.isEmpty()) "USB Host support is advertised, but Android exposes no connected USB device. This is not a filesystem/import failure: the phone has not enumerated the drive. Check the OTG adapter, drive, power and system OTG state."
                    else "Android currently exposes ${usbDevices.size} USB device(s). This is detection only; VaultOS has not opened or modified the drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                usbDevices.forEach { device ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(device.productName ?: device.manufacturerName ?: device.deviceName, fontWeight = FontWeight.Medium)
                        Text(
                            "VID:PID %04X:%04X · %d interface(s) · permission %s".format(
                                device.vendorId, device.productId, device.interfaceCount,
                                if (device.hasPermission) "granted" else "not requested"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Class ${device.deviceClass} / subclass ${device.deviceSubclass} · ${device.deviceName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (device.hasPermission) {
                            device.interfaces.forEach { usbInterface ->
                                Text(
                                    "Interface ${usbInterface.id}: class ${usbInterface.interfaceClass}, subclass ${usbInterface.interfaceSubclass}, protocol ${usbInterface.interfaceProtocol}, ${usbInterface.endpointCount} endpoint(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedButton(onClick = { activity.requestUsbPermission(device.deviceName) }) {
                                Text("Grant access to inspect interfaces")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { usbRefresh++ }) { Text("Refresh USB detection") }
                    Button(enabled = !busy, onClick = { usbTreeLauncher.launch(null) }) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Browse / import USB folder")
                    }
                }
                Text(
                    "The file picker uses Android's Storage Access Framework. VaultOS copies selected files into its private library and never deletes, renames or writes to the source drive.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Automatic indexing", fontWeight = FontWeight.SemiBold)
                Text("Existing shared-storage files are indexed on the first scan. Later discoveries are copied into the private VaultOS archive; original files remain where they are. MediaStore changes trigger scans while the app process is alive, and WorkManager performs periodic catch-up scans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    val request = OneTimeWorkRequestBuilder<StorageScanWorker>().build()
                    WorkManager.getInstance(activity).enqueueUniqueWork("vaultos-storage-scan-now", ExistingWorkPolicy.REPLACE, request)
                    onMessage("Storage scan queued")
                }) { Text("Scan storage now") }
            }
        }
        Text("VaultOS 0.13.0 · Local-first · No account required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Inventory2, null, tint = Color(0xFF718096), modifier = Modifier.size(38.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun captureClipboard(activity: Context, repository: VaultRepository, onMessage: (String) -> Unit) {
    val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip
    if (clip == null || clip.itemCount == 0) { onMessage("Clipboard is empty"); return }
    if (Build.VERSION.SDK_INT >= 33 && runCatching { clip.description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, false) == true }.getOrDefault(false)) {
        onMessage("This clipboard item is marked sensitive and was not saved"); return
    }
    (activity as? ComponentActivity)?.lifecycleScope?.launch {
        val history = ClipboardHistoryRepository(activity)
        val mimeTypes = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
        val label = clip.description.label?.toString()
        val outcomes = (0 until clip.itemCount).map { index ->
            runCatching {
                val item = clip.getItemAt(index)
                val uri = item.uri
                if (uri != null) history.captureUri(uri, null, label, mimeTypes)
                else {
                    val text = item.text?.toString() ?: item.coerceToText(activity)?.toString()
                    if (!text.isNullOrEmpty()) history.captureText(text, null, label, mimeTypes) else null
                }
            }
        }
        val saved = outcomes.count { it.isSuccess && it.getOrNull() != null }
        val failed = outcomes.count { it.isFailure }
        onMessage(if (failed == 0) "Saved $saved clipboard item(s) to history" else "Saved $saved · failed $failed")
    }
}

private fun openItem(context: Context, item: VaultItem, onMessage: (String) -> Unit) {
    val uri = when {
        !item.archivedPath.isNullOrBlank() -> runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.archivedPath)) }.getOrNull()
        !item.originalUri.isNullOrBlank() && item.originalUri.startsWith("content:") -> Uri.parse(item.originalUri)
        !item.originalUri.isNullOrBlank() && item.originalUri.startsWith("file:") -> runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(Uri.parse(item.originalUri).path ?: "")) }.getOrNull()
        else -> null
    }
    if (uri == null) { onMessage("Original file is no longer available"); return }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
        .onFailure { onMessage("No compatible app found to open this file") }
}

private fun shareItem(context: Context, item: VaultItem, onMessage: (String) -> Unit) {
    if (item.category == "Text" || item.category == "Code") {
        val path = item.archivedPath?.let(::File) ?: item.originalUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path?.let(::File) }
        val text = path?.takeIf { it.isFile }?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }
        if (text != null) {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(intent, "Share text")); return
        }
        onMessage("Complete text is unavailable; VaultOS will not share a shortened preview")
        return
    }
    val uri = when {
        !item.archivedPath.isNullOrBlank() -> runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.archivedPath)) }.getOrNull()
        !item.originalUri.isNullOrBlank() && item.originalUri.startsWith("content:") -> Uri.parse(item.originalUri)
        else -> null
    }
    if (uri == null) { onMessage("File is not available to share"); return }
    val intent = Intent(Intent.ACTION_SEND).setType(item.mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share file"))
}

private fun shareDeviceFile(activity: Context, file: File, onMessage: (String) -> Unit) {
    val uri = runCatching { FileProvider.getUriForFile(activity, "${activity.packageName}.files", file) }.getOrNull()
    if (uri == null) { onMessage("Could not create a secure file link"); return }
    val mime = if (file.isDirectory) "resource/folder" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { activity.startActivity(Intent.createChooser(intent, "Share file")) }.onFailure { onMessage("Could not share this file") }
}

private fun openDeviceFile(activity: Context, file: File, onMessage: (String) -> Unit) {
    val uri = runCatching { FileProvider.getUriForFile(activity, "${activity.packageName}.files", file) }.getOrNull()
    if (uri == null) { onMessage("Could not create a secure file link"); return }
    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { activity.startActivity(Intent.createChooser(intent, "Open with")) }.onFailure { onMessage("No application can open this file") }
}

private fun hasAllFilesAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
else context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun openStorageSettings(activity: MainActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}"))) }
            .onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    } else {
        activity.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 701)
    }
}

private fun openAppDetailsSettings(activity: MainActivity) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
    runCatching { activity.startActivity(intent) }
        .onFailure { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

private fun openBatteryOptimizationSettings(activity: MainActivity) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { activity.startActivity(intent) }
        .onFailure { openAppDetailsSettings(activity) }
}

private fun storageAvailable(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)
private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / (1024.0 * 1024))
    else -> "%.2f GB".format(value / (1024.0 * 1024 * 1024))
}

