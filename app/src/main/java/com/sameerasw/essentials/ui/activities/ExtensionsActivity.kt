package com.sameerasw.essentials.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sameerasw.essentials.launcher.CompatibilityReportSheet
import com.sameerasw.essentials.raycast.AndroidCompatLevel
import com.sameerasw.essentials.raycast.CompatibilityReport
import com.sameerasw.essentials.raycast.InstalledRaycastExtension
import com.sameerasw.essentials.raycast.RaycastCompatibilityAnalyzer
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.raycast.RaycastStoreClient
import com.sameerasw.essentials.raycast.StoreExtensionListing
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import kotlinx.coroutines.launch

class ExtensionsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_TAB = "initial_tab"

        fun createIntent(context: Context, openStore: Boolean = false): Intent {
            return Intent(context, ExtensionsActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, if (openStore) 1 else 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)

        setContent {
            val isDark = isSystemInDarkTheme()
            EssentialsTheme(darkTheme = isDark) {
                ExtensionsHubScreen(
                    initialTab = initialTab,
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionsHubScreen(
    initialTab: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var installed by remember { mutableStateOf(RaycastExtensionRepository.listInstalledExtensions(context)) }

    var storeSource by remember { mutableStateOf("raycast") } // "raycast", "vicinae", or "komorebi"
    var storeQuery by remember { mutableStateOf("") }
    var storeListings by remember { mutableStateOf<List<StoreExtensionListing>>(emptyList()) }
    var storeLoading by remember { mutableStateOf(false) }
    var storeError by remember { mutableStateOf<String?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var inspectingCompat by remember { mutableStateOf<Pair<String, CompatibilityReport>?>(null) }
    var preferencesTarget by remember { mutableStateOf<InstalledRaycastExtension?>(null) }
    var prefsVersion by remember { mutableIntStateOf(0) }

    fun refreshInstalled() {
        installed = RaycastExtensionRepository.listInstalledExtensions(context)
    }

    var storeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun loadStore() {
        storeJob?.cancel()
        storeJob = scope.launch {
            storeLoading = true
            storeError = null
            val res = when (storeSource) {
                "vicinae" -> RaycastStoreClient.fetchVicinaeListings(query = storeQuery)
                "komorebi" -> RaycastStoreClient.fetchKomorebiListings(query = storeQuery)
                else -> RaycastStoreClient.fetchRaycastListings(query = storeQuery)
            }
            res.onSuccess { storeListings = it }
                .onFailure { storeError = it.message ?: "Failed to load store" }
            storeLoading = false
        }
    }

    LaunchedEffect(selectedTab, storeSource) {
        if (selectedTab == 1) loadStore()
    }

    val storeSourceLabel = when (storeSource) {
        "vicinae" -> "Vicinae"
        "komorebi" -> "Komorebi"
        else -> "Raycast"
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Extensions & Store") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Installed (${installed.size})") },
                        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Store") },
                        icon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                    )
                }
            }
        },
    ) { padding ->
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(installed, key = { "${it.id}_$prefsVersion" }) { ext ->
                    val report = remember(ext.id, ext.manifest) {
                        RaycastCompatibilityAnalyzer.analyzeInstalledExtension(ext)
                    }
                    val missingRequiredPrefs = remember(ext.id, prefsVersion) {
                        RaycastExtensionRepository.hasRequiredPreferencesMissing(context, ext, null) ||
                            ext.manifest.commands.any { cmd ->
                                RaycastExtensionRepository.hasRequiredPreferencesMissing(context, ext, cmd)
                            }
                    }
                    InstalledExtensionCard(
                        extension = ext,
                        compatReport = report,
                        missingRequiredPrefs = missingRequiredPrefs,
                        onInspectCompatibility = {
                            inspectingCompat = ext.displayTitle to report
                        },
                        onOpenPreferences = {
                            preferencesTarget = ext
                        },
                        onRunCommand = { cmdName ->
                            context.startActivity(
                                ExtensionRunnerActivity.createIntent(context, ext.id, cmdName),
                            )
                        },
                        onUninstall = {
                            RaycastExtensionRepository.uninstallExtension(context, ext.id)
                            refreshInstalled()
                            Toast.makeText(context, "Removed ${ext.displayTitle}", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = storeSource == "raycast",
                        onClick = { storeSource = "raycast" },
                        label = { Text("Raycast Store") },
                    )
                    FilterChip(
                        selected = storeSource == "vicinae",
                        onClick = { storeSource = "vicinae" },
                        label = { Text("Vicinae Store") },
                    )
                    FilterChip(
                        selected = storeSource == "komorebi",
                        onClick = { storeSource = "komorebi" },
                        label = { Text("Komorebi") },
                    )
                }
                OutlinedTextField(
                    value = storeQuery,
                    onValueChange = { storeQuery = it },
                    placeholder = { Text("Search $storeSourceLabel extensions...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        FilledTonalButton(
                            onClick = { loadStore() },
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text("Go")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(10.dp))

                when {
                    storeLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    storeError != null -> Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(storeError ?: "Store unavailable", color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = { loadStore() }) { Text("Retry") }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                storeListings,
                                key = { index, item -> "${item.source}_${item.id}_${item.name}_$index" },
                            ) { _, item ->
                                val report = remember(item.source, item.id, item.name) {
                                    RaycastCompatibilityAnalyzer.analyzeStoreListing(item)
                                }
                                StoreListingCard(
                                    listing = item,
                                    compatReport = report,
                                    isInstalling = installingId == item.id,
                                    onInspectCompatibility = {
                                        inspectingCompat = (item.title.ifBlank { item.name }) to report
                                    },
                                    onInstall = {
                                        scope.launch {
                                            installingId = item.id
                                            val result = RaycastStoreClient.installExtension(context, item)
                                            installingId = null
                                            result.onSuccess { ext ->
                                                refreshInstalled()
                                                Toast.makeText(context, "Installed ${ext.displayTitle}", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Install failed", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingCompat?.let { (title, report) ->
        CompatibilityReportSheet(
            title = title,
            report = report,
            onDismiss = { inspectingCompat = null },
        )
    }

    preferencesTarget?.let { ext ->
        ExtensionPreferencesBottomSheet(
            extension = ext,
            command = null,
            onDismissRequest = {
                preferencesTarget = null
                prefsVersion++
            },
        )
    }
}

@Composable
fun InstalledExtensionCard(
    extension: InstalledRaycastExtension,
    compatReport: CompatibilityReport? = null,
    missingRequiredPrefs: Boolean = false,
    onInspectCompatibility: (() -> Unit)? = null,
    onOpenPreferences: (() -> Unit)? = null,
    onRunCommand: (String) -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    val resolvedReport = compatReport ?: remember(extension.id, extension.manifest) {
        RaycastCompatibilityAnalyzer.analyzeInstalledExtension(extension)
    }
    var localInspectOpen by remember { mutableStateOf(false) }
    var localPrefsOpen by remember { mutableStateOf(false) }
    val hasPreferences = remember(extension) {
        !extension.manifest.preferences.isNullOrEmpty() ||
            extension.manifest.commands.any { !it.preferences.isNullOrEmpty() }
    }
    val openPrefs = onOpenPreferences ?: { localPrefsOpen = true }
    val inspectCompat = onInspectCompatibility ?: { localInspectOpen = true }

    OutlinedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "by ${extension.authorName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasPreferences) {
                    IconButton(onClick = openPrefs) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Extension Preferences",
                            tint = if (missingRequiredPrefs) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (!extension.isBuiltInStarter) {
                    IconButton(onClick = onUninstall) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompatibilityBadge(
                    level = resolvedReport.level,
                    onClick = inspectCompat,
                )
                if (missingRequiredPrefs) {
                    Surface(
                        onClick = openPrefs,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "Setup required",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            if (extension.manifest.description.isNotBlank()) {
                Text(
                    text = extension.manifest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            extension.manifest.commands.forEach { cmd ->
                Surface(
                    onClick = { onRunCommand(cmd.name) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd.title.ifBlank { cmd.name },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (cmd.description.isNotBlank()) {
                                Text(
                                    text = cmd.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (localInspectOpen) {
        CompatibilityReportSheet(
            title = extension.displayTitle,
            report = resolvedReport,
            onDismiss = { localInspectOpen = false },
        )
    }
    if (localPrefsOpen) {
        ExtensionPreferencesBottomSheet(
            extension = extension,
            command = null,
            onDismissRequest = { localPrefsOpen = false },
        )
    }
}

@Composable
fun StoreListingCard(
    listing: StoreExtensionListing,
    compatReport: CompatibilityReport? = null,
    isInstalling: Boolean,
    onInspectCompatibility: (() -> Unit)? = null,
    onInstall: () -> Unit,
) {
    val resolvedReport = compatReport ?: remember(listing.source, listing.id, listing.name) {
        RaycastCompatibilityAnalyzer.analyzeStoreListing(listing)
    }
    var localInspectOpen by remember { mutableStateOf(false) }
    val inspectCompat = onInspectCompatibility ?: { localInspectOpen = true }

    OutlinedCard(shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp),
            ) {
                val url = listing.iconUrl
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Extension, contentDescription = null)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = listing.title.ifBlank { listing.name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${listing.author.handle.ifBlank { listing.author.name }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (listing.description.isNotBlank()) {
                    Text(
                        text = listing.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CompatibilityBadge(
                    level = resolvedReport.level,
                    onClick = inspectCompat,
                )
            }
            FilledTonalButton(
                onClick = onInstall,
                enabled = !isInstalling && !listing.downloadUrl.isNullOrBlank(),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Get")
                }
            }
        }
    }

    if (localInspectOpen) {
        CompatibilityReportSheet(
            title = listing.title.ifBlank { listing.name },
            report = resolvedReport,
            onDismiss = { localInspectOpen = false },
        )
    }
}

@Composable
private fun CompatibilityBadge(
    level: AndroidCompatLevel,
    onClick: () -> Unit,
) {
    val (bg, fg, label, icon) = when (level) {
        AndroidCompatLevel.COMPATIBLE -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Android Ready",
            Icons.Rounded.CheckCircle,
        )
        AndroidCompatLevel.PARTIAL -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Partial Support",
            Icons.Rounded.Warning,
        )
        AndroidCompatLevel.INCOMPATIBLE -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Incompatible",
            Icons.Rounded.Error,
        )
    }
    @Suppress("UNCHECKED_CAST")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bg as androidx.compose.ui.graphics.Color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = fg as androidx.compose.ui.graphics.Color,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label as String,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = fg,
            )
        }
    }
}
