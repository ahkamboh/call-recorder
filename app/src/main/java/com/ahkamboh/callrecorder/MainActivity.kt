@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ahkamboh.callrecorder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Screen { FOLDERS, FILES, SETTINGS }

/** In-app playback of one recording at a time. */
class Player {
    private var mp: MediaPlayer? = null
    var playingId: Long? by mutableStateOf(null)
        private set

    fun toggle(ctx: Context, rec: Recording) {
        if (playingId == rec.id) {
            stop()
            return
        }
        stop()
        try {
            mp = MediaPlayer().apply {
                setDataSource(ctx, rec.uri)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingId = rec.id
        } catch (e: Exception) {
            stop()
            toast(ctx, "Cannot play this file")
        }
    }

    fun stop() {
        mp?.release()
        mp = null
        playingId = null
    }

    fun release() = stop()
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private fun durationText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private fun dateText(ctx: Context, ms: Long): String {
    if (System.currentTimeMillis() - ms < DateUtils.MINUTE_IN_MILLIS) {
        return "Just now, " + DateUtils.formatDateTime(ctx, ms, DateUtils.FORMAT_SHOW_TIME)
    }
    return DateUtils.getRelativeDateTimeString(ctx, ms, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
}

private fun toggleAll(sel: Set<Long>, ids: List<Long>): Set<Long> =
    if (ids.all { it in sel }) sel - ids.toSet() else sel + ids

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val player = remember { Player() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var setup by remember { mutableStateOf(Setup.read(ctx)) }
    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var rules by remember { mutableStateOf(Rules.load(ctx)) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.FOLDERS) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCopy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resumeTick, reloadTick) {
        setup = Setup.read(ctx)
        folders = withContext(Dispatchers.IO) { Repo.load(ctx) }
        loaded = true
    }

    // Live refresh: a call that ends while the app is open shows up without leaving the screen.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadTick++
            }
        }
        ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    val allRecordings = remember(folders) { folders.flatMap { it.recordings } }
    val current = folders.firstOrNull { it.number == openFolder }
    val selectedRecs = remember(allRecordings, selected) { allRecordings.filter { it.id in selected } }

    // Folder emptied (e.g. everything deleted): fall back to the folder list.
    LaunchedEffect(current, loaded, screen) {
        if (screen == Screen.FILES && loaded && current == null) screen = Screen.FOLDERS
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        setup = Setup.read(ctx)
    }
    val copyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            val recs = selectedRecs
            scope.launch {
                busy = "Copying ${recs.size} recording${if (recs.size == 1) "" else "s"}…"
                val n = withContext(Dispatchers.IO) { Repo.copyTo(ctx, tree, recs) }
                busy = null
                selected = emptySet()
                snackbar.showSnackbar(if (n == recs.size) "Copied $n recording${if (n == 1) "" else "s"}" else "Copied $n of ${recs.size}")
            }
        }
    }

    fun share(recs: List<Recording>) {
        if (recs.isEmpty()) return
        try {
            ctx.startActivity(Repo.shareIntent(ctx, recs))
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "No app can receive audio files")
        }
    }

    fun openInFiles(sub: String?) {
        if (!Repo.openFolder(ctx, sub)) {
            toast(ctx, "No file manager found. Folder: Music/CallRecordings" + (sub?.let { "/$it" } ?: ""))
        }
    }

    fun saveRules(r: Rules) {
        rules = r
        Rules.save(ctx, r)
    }

    fun goHome() {
        player.stop()
        screen = Screen.FOLDERS
        openFolder = null
    }

    BackHandler(enabled = selected.isNotEmpty() || screen != Screen.FOLDERS) {
        if (selected.isNotEmpty()) selected = emptySet() else goHome()
    }

    Scaffold(
        topBar = {
            when {
                selected.isNotEmpty() -> SelectionBar(
                    count = selected.size,
                    onClose = { selected = emptySet() },
                    onSelectAll = { selected = (current?.recordings ?: allRecordings).map { it.id }.toSet() },
                    onShare = { share(selectedRecs) },
                    onCopy = { confirmCopy = true },
                    onDelete = { confirmDelete = true },
                )
                screen == Screen.SETTINGS -> TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = { IconButton(onClick = { goHome() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
                screen == Screen.FILES -> TopAppBar(
                    title = {
                        Column {
                            Text(current?.title ?: "")
                            val n = current?.recordings?.size ?: 0
                            Text("$n recording${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { goHome() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { openInFiles(current?.number) }) {
                            Icon(painterResource(R.drawable.ic_folder_open), "Open in Files")
                        }
                    },
                )
                else -> TopAppBar(
                    title = { Text("Call Recorder") },
                    actions = {
                        IconButton(onClick = { openInFiles(null) }) { Icon(painterResource(R.drawable.ic_folder_open), "Open in Files") }
                        IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.FOLDERS -> FolderList(
                    folders = folders,
                    loaded = loaded,
                    setup = setup,
                    enabled = enabled,
                    rules = rules,
                    selected = selected,
                    onToggleEnabled = { enabled = it; Prefs.setEnabled(ctx, it) },
                    onGrant = { permLauncher.launch(Setup.PERMISSIONS) },
                    onAccessibility = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpen = { openFolder = it.number; screen = Screen.FILES },
                    onToggleSelect = { f -> selected = toggleAll(selected, f.recordings.map { it.id }) },
                    onShare = { share(it.recordings) },
                )
                Screen.FILES -> current?.let { folder ->
                    FileList(
                        folder = folder,
                        selected = selected,
                        player = player,
                        onPlay = { player.toggle(ctx, it) },
                        onToggleSelect = { r -> selected = if (r.id in selected) selected - r.id else selected + r.id },
                        onShare = { share(listOf(it)) },
                    )
                }
                Screen.SETTINGS -> SettingsScreen(
                    setup = setup,
                    rules = rules,
                    onRules = ::saveRules,
                    onGrant = { permLauncher.launch(Setup.PERMISSIONS) },
                    onAccessibility = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
        }
    }

    if (confirmCopy) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text("Copy ${selected.size} recording${if (selected.size == 1) "" else "s"}") },
            text = {
                Text(
                    "Pick the destination folder on the next screen. Android does not allow the top level of the storage or the Downloads folder itself, so open a sub-folder or create one. One sub-folder per number is kept."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmCopy = false; copyLauncher.launch(null) }) { Text("Choose folder") }
            },
            dismissButton = { TextButton(onClick = { confirmCopy = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} recording${if (selected.size == 1) "" else "s"}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val recs = selectedRecs
                    scope.launch {
                        busy = "Deleting…"
                        player.stop()
                        val n = withContext(Dispatchers.IO) { Repo.delete(ctx, recs) }
                        busy = null
                        selected = emptySet()
                        reloadTick++
                        snackbar.showSnackbar("Deleted $n recording${if (n == 1) "" else "s"}")
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(msg)
                }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Clear selection") } },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = onCopy) { Icon(painterResource(R.drawable.ic_copy), "Copy to folder") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; onSelectAll() })
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun FolderList(
    folders: List<Folder>,
    loaded: Boolean,
    setup: Setup,
    enabled: Boolean,
    rules: Rules,
    selected: Set<Long>,
    onToggleEnabled: (Boolean) -> Unit,
    onGrant: () -> Unit,
    onAccessibility: () -> Unit,
    onOpen: (Folder) -> Unit,
    onToggleSelect: (Folder) -> Unit,
    onShare: (Folder) -> Unit,
) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { StatusCard(setup, enabled, rules, onToggleEnabled, onGrant, onAccessibility) }
        if (loaded && folders.isEmpty()) {
            item {
                Text(
                    "No recordings yet. Make a call and it will show up here, one folder per number.",
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (folders.isNotEmpty()) {
            item {
                Text(
                    "Recordings by number · long-press to select",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(folders, key = { it.number }) { f ->
            val ids = f.recordings.map { it.id }
            val all = ids.all { it in selected }
            val some = !all && ids.any { it in selected }
            val n = f.recordings.size
            val subtitle = listOfNotNull(
                if (f.displayName != null) f.number else null,
                "$n recording${if (n == 1) "" else "s"}",
                durationText(f.totalDurationMs),
                dateText(ctx, f.latest),
            ).joinToString(" · ")
            ListItem(
                modifier = Modifier.combinedClickable(
                    onClick = { if (selected.isNotEmpty()) onToggleSelect(f) else onOpen(f) },
                    onLongClick = { onToggleSelect(f) },
                ),
                leadingContent = {
                    if (selected.isNotEmpty()) {
                        TriStateCheckbox(
                            state = when {
                                all -> ToggleableState.On
                                some -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            },
                            onClick = { onToggleSelect(f) },
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                headlineContent = { Text(f.title, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(subtitle) },
                trailingContent = {
                    if (selected.isEmpty()) IconButton(onClick = { onShare(f) }) { Icon(Icons.Filled.Share, "Share all") }
                },
                colors = if (all) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
            )
        }
    }
}

@Composable
private fun StatusCard(
    setup: Setup,
    enabled: Boolean,
    rules: Rules,
    onToggle: (Boolean) -> Unit,
    onGrant: () -> Unit,
    onAccessibility: () -> Unit,
) {
    val ready = setup.ready
    val active = enabled && ready
    ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_mic), null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Call recording", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !enabled -> "Off – calls are not recorded"
                            ready -> "On – recording ${rules.summary()}"
                            else -> "Setup needed – finish the steps below"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled && !ready) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (!setup.mic || !setup.phone) SetupStep("Allow microphone and phone permissions", "Grant", onGrant)
                if (!setup.service) {
                    SetupStep("Turn on the accessibility service", "Open", onAccessibility)
                    Text(
                        "Find Call Recorder under Downloaded apps. If it says Restricted setting: Settings › Apps › Call Recorder › ⋮ › Allow restricted settings, then try again.",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(text: String, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun FileList(
    folder: Folder,
    selected: Set<Long>,
    player: Player,
    onPlay: (Recording) -> Unit,
    onToggleSelect: (Recording) -> Unit,
    onShare: (Recording) -> Unit,
) {
    val ctx = LocalContext.current
    val selecting = selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Tap to play · long-press to select",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(folder.recordings, key = { it.id }) { r ->
            val checked = r.id in selected
            val playing = player.playingId == r.id
            ListItem(
                modifier = Modifier.combinedClickable(
                    onClick = { if (selecting) onToggleSelect(r) else onPlay(r) },
                    onLongClick = { onToggleSelect(r) },
                ),
                leadingContent = {
                    if (selecting) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleSelect(r) })
                    } else {
                        FilledTonalIconButton(onClick = { onPlay(r) }) {
                            if (playing) Icon(painterResource(R.drawable.ic_pause), "Pause")
                            else Icon(Icons.Filled.PlayArrow, "Play")
                        }
                    }
                },
                headlineContent = { Text(dateText(ctx, r.date)) },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(if (r.outgoing) R.drawable.ic_call_made else R.drawable.ic_call_received),
                            null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${if (r.outgoing) "Outgoing" else "Incoming"} · ${durationText(r.durationMs)} · ${Formatter.formatShortFileSize(ctx, r.size)}"
                        )
                    }
                },
                trailingContent = {
                    if (!selecting) IconButton(onClick = { onShare(r) }) { Icon(Icons.Filled.Share, "Share") }
                },
                colors = if (checked) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
            )
            if (playing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    setup: Setup,
    rules: Rules,
    onRules: (Rules) -> Unit,
    onGrant: () -> Unit,
    onAccessibility: () -> Unit,
) {
    val ctx = LocalContext.current
    var source by remember { mutableIntStateOf(Prefs.source(ctx)) }
    var showType by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val picked = Repo.pickedContact(ctx, uri)
        if (picked == null) toast(ctx, "Could not read that contact") else onRules(rules.plus(picked))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            RulesCard(
                rules = rules,
                callLogGranted = setup.callLog,
                onRules = onRules,
                onPickContact = {
                    try {
                        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: ActivityNotFoundException) {
                        toast(ctx, "No contacts app found")
                    }
                },
                onRecent = { showRecent = true },
                onType = { showType = true },
                onGrant = onGrant,
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Audio source", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Switch if the other side is silent in recordings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Recorder.SOURCES.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { source = value; Prefs.setSource(ctx, value) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = source == value, onClick = { source = value; Prefs.setSource(ctx, value) })
                            Text(label)
                        }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                    PermRow("Accessibility service (required)", setup.service)
                    PermRow("Microphone (required)", setup.mic)
                    PermRow("Phone state – detects calls (required)", setup.phone)
                    PermRow("Call log – numbers, folders, rules", setup.callLog)
                    PermRow("Notifications – shows while recording", setup.notif)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!setup.mic || !setup.phone || !setup.callLog || !setup.notif) {
                            FilledTonalButton(onClick = onGrant) { Text("Grant") }
                        }
                        if (!setup.service) {
                            FilledTonalButton(onClick = onAccessibility) { Text("Accessibility settings") }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Recordings are saved as .m4a in Music/CallRecordings/<number>/ and are visible to any Files or music app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showType) {
        TypeNumberDialog(onDismiss = { showType = false }, onAdd = { onRules(rules.plus(it)); showType = false })
    }
    if (showRecent) {
        RecentCallsDialog(
            existing = rules,
            onDismiss = { showRecent = false },
            onAdd = { onRules(rules.plusAll(it)); showRecent = false },
        )
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RulesCard(
    rules: Rules,
    callLogGranted: Boolean,
    onRules: (Rules) -> Unit,
    onPickContact: () -> Unit,
    onRecent: () -> Unit,
    onType: () -> Unit,
    onGrant: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Which calls to record", style = MaterialTheme.typography.titleMedium)
            ModeRow("All numbers", rules.mode == RuleMode.ALL) { onRules(rules.copy(mode = RuleMode.ALL)) }
            ModeRow("Only the numbers in the list", rules.mode == RuleMode.ONLY) { onRules(rules.copy(mode = RuleMode.ONLY)) }
            ModeRow("All numbers except the list", rules.mode == RuleMode.EXCEPT) { onRules(rules.copy(mode = RuleMode.EXCEPT)) }

            if (rules.mode != RuleMode.ALL) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (!callLogGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Call log permission is needed to know who is calling.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = onGrant) { Text("Grant") }
                    }
                }
                Text(
                    when {
                        rules.numbers.isEmpty() && rules.mode == RuleMode.ONLY -> "The list is empty, so nothing will be recorded."
                        rules.numbers.isEmpty() -> "The list is empty, so every call will be recorded."
                        rules.mode == RuleMode.ONLY -> "Only these numbers are recorded:"
                        else -> "These numbers are never recorded:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rules.numbers.forEach { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.name ?: n.number, style = MaterialTheme.typography.bodyLarge)
                            if (n.name != null) Text(n.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRules(rules.minus(n)) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onPickContact, Modifier.fillMaxWidth()) { Text("Add from contacts") }
                OutlinedButton(onClick = onRecent, Modifier.fillMaxWidth()) { Text("Add from recent calls") }
                OutlinedButton(onClick = onType, Modifier.fillMaxWidth()) { Text("Type a number") }
                if (rules.numbers.isNotEmpty()) {
                    TextButton(onClick = { onRules(rules.copy(numbers = emptyList())) }) { Text("Clear list") }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun TypeNumberDialog(onDismiss: () -> Unit, onAdd: (RuleNumber) -> Unit) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (optional)") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.any { it.isDigit() },
                onClick = { onAdd(RuleNumber(number.trim(), name.trim().ifEmpty { null })) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecentCallsDialog(existing: Rules, onDismiss: () -> Unit, onAdd: (List<RuleNumber>) -> Unit) {
    val ctx = LocalContext.current
    var recent by remember { mutableStateOf<List<RuleNumber>?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { recent = withContext(Dispatchers.IO) { Repo.recentNumbers(ctx) } }
    val list = recent
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent calls") },
        text = {
            when {
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> Text("No recent calls found. Check that the call log permission is granted.")
                else -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { picked = list.map { it.number }.toSet() }) { Text("Select all") }
                        TextButton(onClick = { picked = emptySet() }) { Text("Clear") }
                    }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(list, key = { it.number }) { n ->
                            val already = existing.contains(n.number)
                            val checked = already || n.number in picked
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !already) {
                                    picked = if (n.number in picked) picked - n.number else picked + n.number
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked, enabled = !already,
                                    onCheckedChange = { picked = if (it) picked + n.number else picked - n.number },
                                )
                                Column {
                                    Text(n.name ?: n.number)
                                    if (n.name != null) Text(n.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (already) Text("already in list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { onAdd(list.orEmpty().filter { it.number in picked }) },
            ) { Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
