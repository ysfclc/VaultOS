package com.vaultos.app

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultItem
import com.vaultos.app.keyboard.VaultKeyboardService
import com.vaultos.app.storage.StorageScanWorker
import com.vaultos.app.storage.VaultBackupManager
import com.vaultos.app.storage.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    lateinit var repository: VaultRepository
        private set
    var resumeRevision by mutableIntStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultRepository(this)
        handleIncoming(intent)
        scheduleScanner()
        setContent { VaultApp(this, repository, resumeRevision) }
    }

    override fun onResume() {
        super.onResume()
        resumeRevision++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val request = OneTimeWorkRequestBuilder<StorageScanWorker>().build()
            WorkManager.getInstance(this).enqueueUniqueWork("vaultos-storage-scan-now", ExistingWorkPolicy.KEEP, request)
        }
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

private enum class Page(val label: String) { Home("Home"), Incoming("Incoming"), Files("Files"), Collections("Collections"), Apps("Apps"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultApp(activity: MainActivity, repository: VaultRepository, resumeRevision: Int) {
    val dao = remember { VaultDatabase.get(activity).dao() }
    val items by dao.observeAll().collectAsState(initial = emptyList())
    val count by dao.count().collectAsState(initial = 0)
    val bytes by dao.archivedBytes().collectAsState(initial = 0L)
    var page by remember { mutableStateOf(Page.Home) }
    var query by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var showItem by remember { mutableStateOf<VaultItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val searchItems by dao.search(query).collectAsState(initial = emptyList())
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
                        IconButton(onClick = { searchMode = !searchMode; if (!searchMode) query = "" }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, "Import files") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0D10))
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF11151A)) {
                    listOf(Page.Home, Page.Incoming, Page.Files, Page.Collections, Page.Apps, Page.Settings).forEach { p ->
                        val icon = when (p) {
                            Page.Home -> Icons.Default.Home
                            Page.Incoming -> Icons.Default.Inbox
                            Page.Files -> Icons.Default.Folder
                            Page.Collections -> Icons.Default.Bookmarks
                            Page.Apps -> Icons.Default.Android
                            Page.Settings -> Icons.Default.Settings
                        }
                        NavigationBarItem(selected = page == p, onClick = { page = p }, icon = { Icon(icon, p.label) }, label = { Text(p.label) })
                    }
                }
            },
            containerColor = Color(0xFF0B0D10),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (searchMode) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = { Text("Search names, full text, categories…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                    )
                }
                val visible = if (searchMode && query.isNotBlank()) searchItems else items
                when (page) {
                    Page.Home -> HomePage(count, bytes, storageAvailable(), items.take(8), onPage = { page = it }, onItem = { showItem = it }, onImport = { importLauncher.launch(arrayOf("*/*")) }, onCapture = { captureClipboard(activity, repository) { message = it } })
                    Page.Incoming -> ItemList(visible, onItem = { showItem = it }, onPin = { item, pinned -> activity.lifecycleScope.launch { repository.setPinned(item, pinned) } })
                    Page.Files -> FilesPage(activity, repository, visible.filter { it.category != "Text" }, permissionGranted, onItem = { showItem = it }, onPermission = { openStorageSettings(activity) }, onMessage = { message = it })
                    Page.Collections -> CollectionsPage(items, onCategory = { query = it; searchMode = true; page = Page.Incoming })
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${item.category} · ${formatBytes(item.sizeBytes)} · ${if (item.isArchived) "Archived" else "Indexed"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall)
                        Text(item.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isText) Text(item.textContent?.take(3000).orEmpty().ifBlank { "Text content is preserved in the original file." }, maxLines = 16, overflow = TextOverflow.Ellipsis)
                        else Text(item.archivedPath ?: item.originalUri ?: "Indexed item", maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (isText && item.sizeBytes > 3000) Text("The complete ${item.sizeBytes} byte original is preserved. Copy reads the full file only when requested.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        TextButton(onClick = { showItem = null }) { Text("Close") }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomePage(count: Int, bytes: Long, available: Long, recent: List<VaultItem>, onPage: (Page) -> Unit, onItem: (VaultItem) -> Unit, onImport: () -> Unit, onCapture: () -> Unit) {
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
            Box(Modifier.size(42.dp).background(Color(0xFF222C35), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF9AE6C1)) }
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

@Composable private fun CollectionsPage(items: List<VaultItem>, onCategory: (String) -> Unit) {
    val groups = items.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
    if (groups.isEmpty()) EmptyState("Smart collections", "Imported files are automatically grouped by content type.")
    else Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("Smart collections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Automatically grouped by file type and content", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        LazyColumn { items(groups) { (name, total) ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onCategory(name) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Color(0xFF9AE6C1)); Text(name, Modifier.weight(1f).padding(start = 12.dp)); Text(total.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } }
    }
}

@Composable private fun FilesPage(activity: MainActivity, repository: VaultRepository, indexed: List<VaultItem>, permissionGranted: Boolean, onItem: (VaultItem) -> Unit, onPermission: () -> Unit, onMessage: (String) -> Unit) {
    var browse by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            FilterChip(selected = !browse, onClick = { browse = false }, label = { Text("Library") }, leadingIcon = { Icon(Icons.Default.Inventory2, null) })
            FilterChip(selected = browse, onClick = { browse = true }, label = { Text("Device folders") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
        }
        if (browse) FileBrowserPage(activity, repository, permissionGranted, onPermission, onMessage)
        else ItemList(indexed, onItem = onItem, onPin = { item, pinned -> activity.lifecycleScope.launch { repository.setPinned(item, pinned) } })
    }
}

@Composable private fun FileBrowserPage(activity: MainActivity, repository: VaultRepository, permissionGranted: Boolean, onPermission: () -> Unit, onMessage: (String) -> Unit) {
    var vaultMode by remember { mutableStateOf(false) }
    var current by remember(vaultMode) { mutableStateOf(if (vaultMode) repository.root else Environment.getExternalStorageDirectory()) }
    var refresh by remember { mutableIntStateOf(0) }
    var menuFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var dialogName by remember { mutableStateOf("") }
    var queuedFile by remember { mutableStateOf<File?>(null) }
    var queuedCut by remember { mutableStateOf(false) }

    fun isInsideVault(file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(repository.root.canonicalFile.toPath())
    }.getOrDefault(false)

    fun scheduleVaultSync() {
        val request = OneTimeWorkRequestBuilder<StorageScanWorker>()
            .setInputData(androidx.work.workDataOf(StorageScanWorker.KEY_VAULT_ONLY to true))
            .build()
        WorkManager.getInstance(activity).enqueueUniqueWork("vaultos-vault-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun uniqueTarget(parent: File, name: String): File {
        var candidate = File(parent, name)
        var index = 1
        while (candidate.exists()) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0) File(parent, "${name.substring(0, dot)}_$index${name.substring(dot)}") else File(parent, "${name}_$index")
            index++
        }
        return candidate
    }

    fun pasteQueued() {
        val source = queuedFile ?: return
        val destinationFolder = current
        if (source.canonicalFile == destinationFolder.canonicalFile || (source.isDirectory && destinationFolder.canonicalPath.startsWith(source.canonicalPath + File.separator))) {
            onMessage("Cannot paste a folder into itself")
            return
        }
        val cut = queuedCut
        val touchesVault = isInsideVault(source) || isInsideVault(destinationFolder)
        activity.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val destination = uniqueTarget(destinationFolder, source.name)
                    if (cut && source.renameTo(destination)) Unit
                    else {
                        if (source.isDirectory) source.copyRecursively(destination, overwrite = false)
                        else source.copyTo(destination, overwrite = false)
                        if (cut && !source.deleteRecursively()) throw IllegalStateException("Copied, but could not remove the original")
                    }
                }
            }.onSuccess {
                queuedFile = null
                if (touchesVault) scheduleVaultSync()
                onMessage(if (cut) "Moved ${source.name}" else "Copied ${source.name}")
                refresh++
            }.onFailure { onMessage(it.message ?: "File operation failed") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            FilterChip(selected = !vaultMode, onClick = { vaultMode = false }, label = { Text("Phone storage") })
            FilterChip(selected = vaultMode, onClick = { vaultMode = true }, label = { Text("Vault archive") })
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
                Text(
                    current.absolutePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath).ifBlank { if (vaultMode) "/VaultOS" else "/" },
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
                if (queuedFile != null) TextButton(onClick = { pasteQueued() }) { Text(if (queuedCut) "Move here" else "Paste here") }
                IconButton(onClick = { dialogName = ""; showNewFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
            }
            val children by produceState(initialValue = emptyList<File>(), current.absolutePath, refresh) {
                value = withContext(Dispatchers.IO) {
                    current.listFiles()?.filter { it.canRead() && !it.name.startsWith(".") }
                        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty()
                }
            }
            if (children.isEmpty()) EmptyState("Folder is empty", "New files detected in shared storage are indexed and incoming files are archived.")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(children, key = { it.absolutePath }) { file ->
                    Card(Modifier.fillMaxWidth().clickable {
                        if (file.isDirectory) current = file else openDeviceFile(activity, file, onMessage)
                    }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151A20))) {
                        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = Color(0xFF9AE6C1))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (file.isDirectory) "Folder" else "${formatBytes(file.length())} · ${file.extension.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (file.isFile) IconButton(onClick = {
                                activity.lifecycleScope.launch {
                                    runCatching { repository.archiveIncomingFile(file, "Manually archived from file browser", force = true) }
                                        .onSuccess { onMessage("Archived ${file.name}"); refresh++ }
                                        .onFailure { onMessage(it.message ?: "Archive failed") }
                                }
                            }) { Icon(Icons.Default.Archive, "Archive a copy") }
                            Box {
                                IconButton(onClick = { menuFile = file }) { Icon(Icons.Default.MoreVert, "File actions") }
                                DropdownMenu(expanded = menuFile == file, onDismissRequest = { menuFile = null }) {
                                    if (file.isFile) DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuFile = null; shareDeviceFile(activity, file, onMessage) })
                                    DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { queuedFile = file; queuedCut = false; menuFile = null; onMessage("${file.name} ready to paste") })
                                    DropdownMenuItem(text = { Text("Cut") }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = { queuedFile = file; queuedCut = true; menuFile = null; onMessage("${file.name} ready to move") })
                                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { renameTarget = file; dialogName = file.name; menuFile = null })
                                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { deleteTarget = file; menuFile = null })
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
            onMessage(if (ok) "Renamed to $safe" else "Could not rename file")
        }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
    ) }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${target.name}?") },
        text = { Text("This permanently deletes the selected ${if (target.isDirectory) "folder and its contents" else "file"} from its original location. This cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            val deleted = runCatching { if (target.isDirectory) target.deleteRecursively() else target.delete() }.getOrDefault(false)
            if (deleted && vaultMode) scheduleVaultSync()
            deleteTarget = null; refresh++
            onMessage(if (deleted) "Deleted ${target.name}" else "Could not delete ${target.name}")
        }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
    ) }
}

@Composable private fun AppsPage(activity: MainActivity, repository: VaultRepository, onMessage: (String) -> Unit) {
    val apps = remember { runCatching { activity.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).sortedBy { activity.packageManager.getApplicationLabel(it).toString().lowercase() } }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("Installed applications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${apps.size} visible packages · Archive the base APK; split packages may need additional components.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, tint = Color(0xFF9AE6C1), modifier = Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(activity.packageManager.getApplicationLabel(app).toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) }) { Icon(Icons.Default.Info, "App details") }
                    IconButton(onClick = {
                        activity.lifecycleScope.launch {
                            val packagePaths = buildList {
                                app.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                                app.splitSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
                            }.distinct()
                            if (packagePaths.isEmpty()) onMessage("APK source is unavailable")
                            else runCatching {
                                packagePaths.forEach { path ->
                                    repository.archiveIncomingFile(File(path), "Installed application package · ${app.packageName}", force = true)
                                        ?: throw IllegalStateException("Could not read APK component $path")
                                }
                            }.onSuccess { onMessage("Archived ${packagePaths.size} APK component(s) for ${activity.packageManager.getApplicationLabel(app)}") }
                                .onFailure { onMessage(it.message ?: "APK export failed") }
                        }
                    }) { Icon(Icons.Default.Archive, "Archive APK") }
                }
                Divider(color = Color(0xFF252C34))
            }
        }
    }
}

@Composable private fun SettingsPage(activity: MainActivity, repository: VaultRepository, permissionGranted: Boolean, backupLauncher: androidx.activity.result.ActivityResultLauncher<Uri?>, onMessage: (String) -> Unit) {
    val prefs = activity.getSharedPreferences(VaultKeyboardService.PREFS, Context.MODE_PRIVATE)
    var autoCapture by remember { mutableStateOf(prefs.getBoolean(VaultKeyboardService.KEY_AUTO_CAPTURE, true)) }
    var busy by remember { mutableStateOf(false) }
    val backupTree = prefs.getString("backup_tree", null)
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Automatically save clipboard content"); Text("On when VaultOS is the active keyboard. Captures text and shared clipboard attachments; sensitive clips and password fields are skipped when detectable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = autoCapture, onCheckedChange = { autoCapture = it; prefs.edit().putBoolean(VaultKeyboardService.KEY_AUTO_CAPTURE, it).apply() })
                }
                OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) { Text("Manage keyboards") }
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
        Text("VaultOS 0.1.0 · Local-first · No account required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    (activity as? ComponentActivity)?.lifecycleScope?.launch {
        val result = runCatching {
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                val uri = item.uri
                if (uri != null) repository.importUri(uri, "Manual clipboard file capture")
                else {
                    val text = item.text?.toString() ?: item.coerceToText(activity)?.toString()
                    if (!text.isNullOrEmpty()) repository.saveText(text, "Manual clipboard capture")
                }
            }
        }
        result.onSuccess { onMessage("Clipboard content saved") }
            .onFailure { onMessage(it.message ?: "Clipboard capture failed") }
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

private fun storageAvailable(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)
private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / (1024.0 * 1024))
    else -> "%.2f GB".format(value / (1024.0 * 1024 * 1024))
}

