package com.sameerasw.essentials.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sameerasw.essentials.raycast.AndroidCompatLevel
import com.sameerasw.essentials.raycast.CompatibilityReport
import com.sameerasw.essentials.utils.HapticUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherToolModalOverlay(
    modal: LauncherToolModal,
    initialQuery: String = "",
    reachabilityLayout: Boolean = false,
    onDismiss: () -> Unit,
    onCopiedAndClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            when (modal) {
                LauncherToolModal.CLIPBOARD_HISTORY -> ClipboardHistoryOverlay(
                    initialQuery = initialQuery,
                    reachabilityLayout = reachabilityLayout,
                    onCopiedAndClose = onCopiedAndClose,
                )
                LauncherToolModal.EMOJI_PICKER -> EmojiPickerOverlay(
                    initialQuery = initialQuery,
                    reachabilityLayout = reachabilityLayout,
                    onCopiedAndClose = onCopiedAndClose,
                )
                LauncherToolModal.QUICKLINKS -> QuicklinksOverlay(
                    initialQuery = initialQuery,
                    reachabilityLayout = reachabilityLayout,
                    onLaunchedAndClose = onCopiedAndClose,
                )
                LauncherToolModal.SNIPPETS -> SnippetsOverlay(
                    initialQuery = initialQuery,
                    reachabilityLayout = reachabilityLayout,
                    onCopiedAndClose = onCopiedAndClose,
                )
                LauncherToolModal.BUS_TIMES -> BusTimesOverlay(
                    reachabilityLayout = reachabilityLayout,
                )
                LauncherToolModal.SPEED_TEST -> SpeedTestOverlay(
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ClipboardHistoryOverlay(
    initialQuery: String,
    reachabilityLayout: Boolean,
    onCopiedAndClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var query by remember { mutableStateOf(initialQuery) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<ClipboardEntry>>(emptyList()) }

    LaunchedEffect(query, refreshTick) {
        ClipboardHistoryManager.captureCurrent(context)
        entries = ClipboardHistoryManager.search(context, query)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Clipboard History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${entries.size} item${if (entries.size == 1) "" else "s"} saved locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    HapticUtil.performVirtualKeyHaptic(view)
                    ClipboardHistoryManager.clearUnpinned(context)
                    refreshTick++
                    Toast.makeText(context, "Cleared unpinned items", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Clear unpinned")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search clipboard history...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (entries.isEmpty()) {
            EmptyToolState(
                title = if (query.isBlank()) "Clipboard history is empty" else "No matching clips",
                subtitle = if (query.isBlank()) "Copied text, links, code, and colors will appear here." else "Try a different search term.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = reachabilityLayout,
            ) {
                items(entries, key = { it.id }) { entry ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                HapticUtil.performConfirmHaptic(view)
                                ClipboardHistoryManager.copyEntry(context, entry)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                onCopiedAndClose()
                            },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            text = entry.contentType.uppercase(Locale.US),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                    if (entry.pinned) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = "PINNED",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatRelativeTime(entry.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = entry.text,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = if (entry.contentType == "code" || entry.contentType == "color") {
                                        FontFamily.Monospace
                                    } else {
                                        FontFamily.Default
                                    },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        ClipboardHistoryManager.togglePin(context, entry.id)
                                        refreshTick++
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = if (entry.pinned) "Unpin" else "Pin",
                                        tint = if (entry.pinned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        ClipboardHistoryManager.deleteEntry(context, entry.id)
                                        refreshTick++
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerOverlay(
    initialQuery: String,
    reachabilityLayout: Boolean,
    onCopiedAndClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var query by remember { mutableStateOf(initialQuery.removePrefix(":").trim()) }
    var selectedCategory by remember { mutableStateOf("All") }
    val recents = remember { EmojiCatalog.getRecents(context) }
    val categories = remember(recents) {
        buildList {
            add("All")
            if (recents.isNotEmpty()) add("Recent")
            addAll(EmojiCatalog.categories())
        }
    }

    val filtered = remember(query, selectedCategory, recents) {
        val base = if (query.isNotBlank()) {
            EmojiCatalog.search(query)
        } else {
            when (selectedCategory) {
                "Recent" -> recents
                "All" -> EmojiCatalog.all()
                else -> EmojiCatalog.all().filter { it.category == selectedCategory }
            }
        }
        if (query.isNotBlank() && selectedCategory != "All" && selectedCategory != "Recent") {
            base.filter { it.category == selectedCategory }
        } else {
            base
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = "Emoji & Symbols",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tap any emoji to copy to clipboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search emojis (e.g. fire, rocket, heart)...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        selectedCategory = category
                    },
                    label = { Text(category) },
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyToolState(
                title = "No matching emojis",
                subtitle = "Try another keyword or clear the category filter.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 54.dp),
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = reachabilityLayout,
            ) {
                items(filtered, key = { "${it.category}_${it.emoji}_${it.name}" }) { entry ->
                    Surface(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                HapticUtil.performConfirmHaptic(view)
                                copyText(context, entry.name, entry.emoji)
                                EmojiCatalog.recordUsage(context, entry.emoji)
                                ClipboardHistoryManager.recordCopiedText(context, entry.emoji)
                                Toast.makeText(context, "Copied ${entry.emoji} (${entry.name})", Toast.LENGTH_SHORT).show()
                                onCopiedAndClose()
                            },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.emoji,
                                fontSize = 26.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuicklinksOverlay(
    initialQuery: String,
    reachabilityLayout: Boolean,
    onLaunchedAndClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var argumentText by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    var links by remember { mutableStateOf<List<Quicklink>>(emptyList()) }
    var editingLink by remember { mutableStateOf<Quicklink?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, refreshTick) {
        links = QuicklinksRepository.search(context, searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Quicklinks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Launch search templates & custom web shortcuts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = {
                    HapticUtil.performVirtualKeyHaptic(view)
                    editingLink = Quicklink(id = "", name = "", urlTemplate = "https://", keyword = "")
                    showEditor = true
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Filter quicklinks by name or keyword...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        OutlinedTextField(
            value = argumentText,
            onValueChange = { argumentText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Optional query for {query} placeholder...") },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        if (links.isEmpty()) {
            EmptyToolState(
                title = "No quicklinks found",
                subtitle = "Create a new quicklink with {query} to launch fast web searches.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = reachabilityLayout,
            ) {
                items(links, key = { it.id }) { link ->
                    val previewUrl = QuicklinksRepository.resolveUrl(link.urlTemplate, argumentText)
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                HapticUtil.performConfirmHaptic(view)
                                if (QuicklinksRepository.launch(context, link, argumentText)) {
                                    onLaunchedAndClose()
                                } else {
                                    Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = link.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (link.keyword.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = link.keyword,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = previewUrl,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editingLink = link
                                        showEditor = true
                                    },
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                }
                                IconButton(
                                    onClick = {
                                        QuicklinksRepository.delete(context, link.id)
                                        refreshTick++
                                    },
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        HapticUtil.performConfirmHaptic(view)
                                        if (QuicklinksRepository.launch(context, link, argumentText)) {
                                            onLaunchedAndClose()
                                        } else {
                                            Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Open")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor && editingLink != null) {
        QuicklinkEditorDialog(
            initial = editingLink!!,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                QuicklinksRepository.save(context, updated)
                showEditor = false
                refreshTick++
            },
        )
    }
}

@Composable
private fun QuicklinkEditorDialog(
    initial: Quicklink,
    onDismiss: () -> Unit,
    onSave: (Quicklink) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var keyword by remember { mutableStateOf(initial.keyword) }
    var urlTemplate by remember { mutableStateOf(initial.urlTemplate) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New Quicklink" else "Edit Quicklink") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword alias (e.g. gh, yt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it; errorText = null },
                    label = { Text("URL template") },
                    supportingText = { Text("Use {query} or {clipboard} placeholders") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || urlTemplate.isBlank()) {
                        errorText = "Name and URL template are required."
                        return@Button
                    }
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            keyword = keyword.trim(),
                            urlTemplate = urlTemplate.trim(),
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BusTimesOverlay(reachabilityLayout: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var stops by remember { mutableStateOf<List<NearbyBusStop>>(emptyList()) }
    var selectedStop by remember { mutableStateOf<NearbyBusStop?>(null) }
    var departures by remember { mutableStateOf<List<BusDeparture>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var departuresLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val registryOwner = LocalActivityResultRegistryOwner.current
    val requestLocationPermissions: () -> Unit = if (registryOwner != null) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            hasPermission = result.values.any { it } || hasLocationPermission(context)
            if (hasPermission) refreshKey++
        }
        val launchPermissions: () -> Unit = {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
        launchPermissions
    } else {
        val openAppSettings: () -> Unit = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        openAppSettings
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (!hasPermission) return@LaunchedEffect
        loading = true
        error = null
        selectedStop = null
        departures = emptyList()
        val location = getCurrentLocation(context)
        if (location == null) {
            loading = false
            error = "Could not get your location. Check that location is enabled."
            return@LaunchedEffect
        }
        stops = runCatching {
            BusTimesRepository.findNearbyStops(location.latitude, location.longitude)
        }.getOrElse {
            error = "Could not load nearby stops. Check your connection."
            emptyList()
        }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedStop?.name ?: "Nearby bus times",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedStop?.let { "${it.distanceMeters} m away" }
                        ?: "Live departures from stops around you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedStop != null) {
                TextButton(
                    onClick = {
                        selectedStop = null
                        departures = emptyList()
                        error = null
                    },
                ) { Text("Back") }
            }
            IconButton(
                onClick = { refreshKey++ },
                enabled = hasPermission && !loading,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }

        when {
            !hasPermission -> {
                EmptyToolState(
                    title = "Location permission needed",
                    subtitle = "Komorebi uses your approximate location to find nearby bus stops. No accessibility service is used.",
                )
                Button(
                    onClick = requestLocationPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow location") }
            }

            loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Finding nearby stops…", style = MaterialTheme.typography.bodySmall)
            }

            selectedStop != null -> {
                val stop = selectedStop!!
                if (departuresLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (stop.stopCode == null) {
                    EmptyToolState(
                        title = "Live times unavailable",
                        subtitle = "This stop does not expose a stop code. You can still open it in bustimes.org or your maps app.",
                    )
                } else if (!departuresLoading && error != null) {
                    EmptyToolState(title = "Could not load live times", subtitle = error!!)
                } else if (!departuresLoading && departures.isEmpty()) {
                    EmptyToolState(
                        title = "No departures found",
                        subtitle = "There are no upcoming departures listed for this stop right now.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = reachabilityLayout,
                    ) {
                        items(departures, key = { "${it.route}:${it.scheduled}:${it.destination}" }) { departure ->
                            OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = departure.route,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = departure.destination.ifBlank { "Unknown destination" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (departure.isLive) {
                                                "Scheduled ${departure.scheduled}"
                                            } else {
                                                "Scheduled"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = departure.expected.ifBlank { departure.scheduled },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (departure.isLive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                val fallbackUrl = stop.website
                    ?: "https://bustimes.org/search?q=${Uri.encode(stop.name)}"
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (stop.website != null) "Open live departures" else "Search on bustimes.org") }
            }

            error != null -> {
                EmptyToolState(title = "Could not load bus times", subtitle = error!!)
                Button(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }

            stops.isEmpty() -> {
                EmptyToolState(
                    title = "No bus stops nearby",
                    subtitle = "No bus stops were found within about 900 m of your location.",
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = reachabilityLayout,
                ) {
                    items(stops, key = { it.id }) { stop ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    selectedStop = stop
                                    departures = emptyList()
                                    error = null
                                    val stopCode = stop.stopCode
                                    if (stopCode != null) {
                                        departuresLoading = true
                                        scope.launch {
                                            departures = runCatching {
                                                BusTimesRepository.departures(stopCode)
                                            }.getOrElse {
                                                error = "Could not load live departures."
                                                emptyList()
                                            }
                                            departuresLoading = false
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stop.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append("${stop.distanceMeters} m")
                                            if (stop.stopCode != null) append(" • live times")
                                            else append(" • no live stop code")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetsOverlay(
    initialQuery: String,
    reachabilityLayout: Boolean,
    onCopiedAndClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var query by remember { mutableStateOf(initialQuery) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var snippets by remember { mutableStateOf<List<TextSnippet>>(emptyList()) }
    var editingSnippet by remember { mutableStateOf<TextSnippet?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(query, refreshTick) {
        snippets = SnippetsRepository.search(context, query)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Snippets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap any snippet to expand placeholders & copy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = {
                    HapticUtil.performVirtualKeyHaptic(view)
                    editingSnippet = TextSnippet(id = "", name = "", keyword = "!", content = "")
                    showEditor = true
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search snippets or !keyword...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (snippets.isEmpty()) {
            EmptyToolState(
                title = "No matching snippets",
                subtitle = "Create a snippet using {date}, {time}, {datetime}, {uuid}, or {clipboard}.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = reachabilityLayout,
            ) {
                items(snippets, key = { it.id }) { snippet ->
                    val preview = remember(snippet.content) {
                        SnippetsRepository.expandContent(snippet.content, "<clipboard>")
                    }
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                HapticUtil.performConfirmHaptic(view)
                                SnippetsRepository.expandAndCopy(context, snippet)
                                Toast.makeText(context, "Expanded & copied \"${snippet.name}\"", Toast.LENGTH_SHORT).show()
                                onCopiedAndClose()
                            },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = snippet.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (snippet.keyword.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = snippet.keyword,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = preview,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editingSnippet = snippet
                                        showEditor = true
                                    },
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                }
                                IconButton(
                                    onClick = {
                                        SnippetsRepository.delete(context, snippet.id)
                                        refreshTick++
                                    },
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        HapticUtil.performConfirmHaptic(view)
                                        SnippetsRepository.expandAndCopy(context, snippet)
                                        Toast.makeText(context, "Expanded & copied", Toast.LENGTH_SHORT).show()
                                        onCopiedAndClose()
                                    },
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor && editingSnippet != null) {
        SnippetEditorDialog(
            initial = editingSnippet!!,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                SnippetsRepository.save(context, updated)
                showEditor = false
                refreshTick++
            },
        )
    }
}

@Composable
private fun SnippetEditorDialog(
    initial: TextSnippet,
    onDismiss: () -> Unit,
    onSave: (TextSnippet) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var keyword by remember { mutableStateOf(initial.keyword) }
    var content by remember { mutableStateOf(initial.content) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New Snippet" else "Edit Snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Trigger keyword (e.g. !sig)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; errorText = null },
                    label = { Text("Snippet body") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("{date}", "{time}", "{datetime}", "{uuid}", "{clipboard}").forEach { token ->
                        AssistChip(
                            onClick = { content += token },
                            label = { Text(token, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || content.isBlank()) {
                        errorText = "Name and snippet body are required."
                        return@Button
                    }
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            keyword = keyword.trim(),
                            content = content,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SpeedTestOverlay(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf("Ready") }
    var progress by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableStateOf<Double?>(null) }
    var jitterMs by remember { mutableStateOf<Double?>(null) }
    var downloadMbps by remember { mutableDoubleStateOf(0.0) }
    var uploadMbps by remember { mutableDoubleStateOf(0.0) }
    var hasDownload by remember { mutableStateOf(false) }
    var hasUpload by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun startTest() {
        activeJob?.cancel()
        activeJob = scope.launch {
            running = true
            errorMessage = null
            progress = 0.05f
            pingMs = null
            jitterMs = null
            downloadMbps = 0.0
            uploadMbps = 0.0
            hasDownload = false
            hasUpload = false

            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            try {
                // 1. Latency & Jitter
                phase = "Measuring latency..."
                val samples = withContext(Dispatchers.IO) {
                    val pings = mutableListOf<Double>()
                    repeat(5) { idx ->
                        if (!isActive) throw CancellationException()
                        val req = Request.Builder()
                            .url("https://speed.cloudflare.com/__down?bytes=0&cb=${System.nanoTime()}")
                            .header("Cache-Control", "no-cache")
                            .get()
                            .build()
                        val startNs = System.nanoTime()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) error("Ping HTTP ${resp.code}")
                            resp.body?.close()
                        }
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                        if (idx > 0) pings.add(elapsedMs) // skip TLS cold start on first ping
                        withContext(Dispatchers.Main) {
                            progress = 0.08f + (idx + 1) * 0.04f
                        }
                    }
                    pings
                }
                if (samples.isNotEmpty()) {
                    val avg = samples.average()
                    val jitter = if (samples.size > 1) {
                        samples.zipWithNext { a, b -> abs(a - b) }.average()
                    } else {
                        0.0
                    }
                    pingMs = avg
                    jitterMs = jitter
                }

                // 2. Download
                phase = "Testing download..."
                withContext(Dispatchers.IO) {
                    val targetBytes = 10_000_000L
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=$targetBytes&cb=${System.nanoTime()}")
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("Download HTTP ${resp.code}")
                        val body = resp.body ?: error("Empty download body")
                        val stream = body.byteStream()
                        val buf = ByteArray(32 * 1024)
                        var totalRead = 0L
                        val startNs = System.nanoTime()
                        var lastUiNs = startNs
                        while (isActive) {
                            val read = stream.read(buf)
                            if (read <= 0) break
                            totalRead += read
                            val nowNs = System.nanoTime()
                            val elapsedSec = ((nowNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                            if (nowNs - lastUiNs > 120_000_000L || totalRead >= targetBytes) {
                                lastUiNs = nowNs
                                val mbps = (totalRead * 8.0) / (elapsedSec * 1_000_000.0)
                                val dlFrac = (totalRead.toFloat() / targetBytes.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    hasDownload = true
                                    downloadMbps = mbps
                                    progress = 0.28f + dlFrac * 0.36f
                                }
                            }
                            if (elapsedSec > 6.5) break
                        }
                        val totalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                        val finalMbps = (totalRead * 8.0) / (totalSec * 1_000_000.0)
                        withContext(Dispatchers.Main) {
                            hasDownload = true
                            downloadMbps = finalMbps
                            progress = 0.64f
                        }
                    }
                }

                // 3. Upload
                phase = "Testing upload..."
                withContext(Dispatchers.IO) {
                    val uploadTotalBytes = 2_000_000L
                    val chunk = ByteArray(32 * 1024) { 0x5A }
                    val startNs = System.nanoTime()
                    val body = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength(): Long = uploadTotalBytes
                        override fun writeTo(sink: BufferedSink) {
                            var sent = 0L
                            var lastUiNs = startNs
                            while (sent < uploadTotalBytes && isActive) {
                                val toWrite = minOf(chunk.size.toLong(), uploadTotalBytes - sent).toInt()
                                sink.write(chunk, 0, toWrite)
                                sent += toWrite
                                val nowNs = System.nanoTime()
                                val elapsedSec = ((nowNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                                if (nowNs - lastUiNs > 120_000_000L || sent >= uploadTotalBytes) {
                                    lastUiNs = nowNs
                                    val mbps = (sent * 8.0) / (elapsedSec * 1_000_000.0)
                                    val ulFrac = (sent.toFloat() / uploadTotalBytes.toFloat()).coerceIn(0f, 1f)
                                    scope.launch(Dispatchers.Main) {
                                        hasUpload = true
                                        uploadMbps = mbps
                                        progress = 0.64f + ulFrac * 0.36f
                                    }
                                }
                            }
                        }
                    }
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__up")
                        .post(body)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("Upload HTTP ${resp.code}")
                    }
                    val totalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                    val finalMbps = (uploadTotalBytes * 8.0) / (totalSec * 1_000_000.0)
                    withContext(Dispatchers.Main) {
                        hasUpload = true
                        uploadMbps = finalMbps
                        progress = 1f
                    }
                }

                phase = "Complete"
            } catch (_: CancellationException) {
                phase = "Cancelled"
            } catch (t: Throwable) {
                errorMessage = t.localizedMessage ?: "Network error while measuring speed"
                phase = "Failed"
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(Unit) {
        startTest()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Internet Speed Test",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = phase,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Cloudflare Edge",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedMetricCard(
                modifier = Modifier.weight(1f),
                label = "Download",
                value = if (hasDownload) String.format(Locale.US, "%.1f", downloadMbps) else "--",
                unit = "Mbps",
            )
            SpeedMetricCard(
                modifier = Modifier.weight(1f),
                label = "Upload",
                value = if (hasUpload) String.format(Locale.US, "%.1f", uploadMbps) else "--",
                unit = "Mbps",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedMetricCard(
                modifier = Modifier.weight(1f),
                label = "Ping",
                value = pingMs?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                unit = "ms",
            )
            SpeedMetricCard(
                modifier = Modifier.weight(1f),
                label = "Jitter",
                value = jitterMs?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                unit = "ms",
            )
        }

        if (errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (running) {
                OutlinedButton(
                    onClick = {
                        activeJob?.cancel()
                        running = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = {
                        HapticUtil.performVirtualKeyHaptic(view)
                        startTest()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Again")
                }
            }

            if (hasDownload) {
                FilledTonalButton(
                    onClick = {
                        HapticUtil.performConfirmHaptic(view)
                        val summary = buildString {
                            append("Download: ").append(String.format(Locale.US, "%.1f Mbps", downloadMbps))
                            if (hasUpload) append(" | Upload: ").append(String.format(Locale.US, "%.1f Mbps", uploadMbps))
                            pingMs?.let { append(" | Ping: ").append(String.format(Locale.US, "%.0f ms", it)) }
                            jitterMs?.let { append(" | Jitter: ").append(String.format(Locale.US, "%.1f ms", it)) }
                        }
                        copyText(context, "Speed Test Result", summary)
                        ClipboardHistoryManager.recordCopiedText(context, summary)
                        Toast.makeText(context, "Copied speed test results", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            }

            TextButton(onClick = {
                activeJob?.cancel()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = unit,
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompatibilityReportSheet(
    title: String,
    report: CompatibilityReport,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val (badgeBg, badgeFg, badgeLabel, badgeIcon) = when (report.level) {
                AndroidCompatLevel.COMPATIBLE -> Quad(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    "Full Android Support",
                    Icons.Rounded.CheckCircle,
                )
                AndroidCompatLevel.PARTIAL -> Quad(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    "Partial Support",
                    Icons.Rounded.Warning,
                )
                AndroidCompatLevel.INCOMPATIBLE -> Quad(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    "Incompatible on Android",
                    Icons.Rounded.Error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Android Compatibility Diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(badgeIcon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(16.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeFg,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = report.summary,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (report.detectedApis.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detected APIs & Integrations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        report.detectedApis.forEach { api ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = api,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (report.issues.isEmpty()) {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No macOS-only binaries, AppleScript, or unsupported desktop APIs were detected.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detected Blockers & Warnings (${report.issues.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    report.issues.forEach { issue ->
                        val (bg, fg, label) = when (issue.severity) {
                            AndroidCompatLevel.COMPATIBLE -> Triple(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                "INFO",
                            )
                            AndroidCompatLevel.PARTIAL -> Triple(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                                "WARNING",
                            )
                            AndroidCompatLevel.INCOMPATIBLE -> Triple(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                "BLOCKER",
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = fg,
                                        )
                                    }
                                    Text(
                                        text = issue.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    text = issue.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Code: ${issue.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun EmptyToolState(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = ContextCompat.getSystemService(context.applicationContext, ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun formatRelativeTime(timestampMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86400L}d ago"
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
