package com.sameerasw.essentials.raycast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaycastScreenHost(
    screen: RaycastViewScreen,
    extension: InstalledRaycastExtension,
    commandTitle: String,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onInvokeCallback: (String, Array<Any?>) -> Unit,
    onOpenPreferences: () -> Unit,
) {
    var activeActionPanel by remember { mutableStateOf<RaycastActionPanelSpec?>(null) }
    var selectedItemId by remember(screen::class) {
        mutableStateOf((screen as? RaycastViewScreen.ListScreen)?.selectedItemId)
    }
    LaunchedEffect((screen as? RaycastViewScreen.ListScreen)?.selectedItemId) {
        val explicit = (screen as? RaycastViewScreen.ListScreen)?.selectedItemId
        if (explicit != null) selectedItemId = explicit
    }

    val primaryPanel = rememberRaycastPrimaryPanel(screen, selectedItemId)
    val formState = rememberRaycastFormState(screen)

    fun executeAction(action: RaycastActionSpec) {
        executeRaycastAction(action, formState, onInvokeCallback)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = screen.navigationTitle?.takeIf { it.isNotBlank() } ?: commandTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = extension.displayTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = if (canNavigateBack) "Back" else "Close",
                            )
                        }
                    },
                    actions = {
                        if (!extension.manifest.preferences.isNullOrEmpty()) {
                            IconButton(onClick = onOpenPreferences) {
                                Icon(Icons.Rounded.Tune, contentDescription = "Extension Preferences")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (screen.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            val panel = primaryPanel
            val primary = panel?.primaryAction
            if (panel != null && (primary != null || panel.sections.isNotEmpty() || panel.submenus.isNotEmpty())) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (primary != null) {
                            Button(
                                onClick = { executeAction(primary) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                RaycastIconView(
                                    spec = primary.icon,
                                    extension = extension,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(primary.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        FilledTonalButton(
                            onClick = { activeActionPanel = panel },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Actions")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RaycastScreenBody(
                screen = screen,
                extension = extension,
                selectedItemId = selectedItemId,
                formState = formState,
                onSelectItem = { selectedItemId = it },
                onOpenActionPanel = { activeActionPanel = it },
                onInvokeCallback = onInvokeCallback,
            )
        }
    }

    activeActionPanel?.let { panel ->
        RaycastActionPanelSheet(
            panel = panel,
            extension = extension,
            onDismissRequest = { activeActionPanel = null },
            onExecuteAction = { action ->
                activeActionPanel = null
                executeAction(action)
            },
        )
    }
}

@Composable
fun rememberRaycastPrimaryPanel(
    screen: RaycastViewScreen,
    selectedItemId: String?,
): RaycastActionPanelSpec? = when (screen) {
    is RaycastViewScreen.ListScreen -> {
        val allItems = screen.sections.flatMap { it.items }
        val focused = allItems.firstOrNull { it.id == selectedItemId } ?: allItems.firstOrNull()
        focused?.actions ?: (if (allItems.isEmpty()) screen.emptyView?.actions else null) ?: screen.actions
    }
    is RaycastViewScreen.GridScreen -> {
        val allItems = screen.sections.flatMap { it.items }
        val focused = allItems.firstOrNull { it.id == selectedItemId } ?: allItems.firstOrNull()
        focused?.actions ?: (if (allItems.isEmpty()) screen.emptyView?.actions else null) ?: screen.actions
    }
    is RaycastViewScreen.DetailScreen -> screen.actions
    is RaycastViewScreen.FormScreen -> screen.actions
}

@Composable
fun rememberRaycastFormState(screen: RaycastViewScreen): MutableMap<String, Any> {
    val fieldIds = (screen as? RaycastViewScreen.FormScreen)?.fields?.map { it.id }
    val formState = remember(screen::class, fieldIds) { mutableStateMapOf<String, Any>() }
    if (screen is RaycastViewScreen.FormScreen) {
        screen.fields.forEach { field ->
            val shouldInitialize = !formState.containsKey(field.id)
            if (shouldInitialize || field.hasControlledValue) {
                when (field.kind) {
                    "checkbox" -> formState[field.id] = field.boolValue ?: false
                    "tag-picker" -> formState[field.id] = field.tagValues
                    "dropdown" -> formState[field.id] = field.textValue
                        ?: field.options.firstOrNull()?.value
                        ?: ""
                    else -> formState[field.id] = field.textValue ?: ""
                }
            }
        }
    }
    return formState
}

fun executeRaycastAction(
    action: RaycastActionSpec,
    formState: Map<String, Any>,
    onInvokeCallback: (String, Array<Any?>) -> Unit,
) {
    if (action.isSubmit && action.onSubmitId != null) {
        onInvokeCallback(action.onSubmitId, arrayOf(formState.toMap()))
    } else if (action.onActionId != null) {
        onInvokeCallback(action.onActionId, emptyArray())
    }
}

@Composable
fun RaycastScreenBody(
    screen: RaycastViewScreen,
    extension: InstalledRaycastExtension,
    selectedItemId: String?,
    formState: MutableMap<String, Any>,
    onSelectItem: (String) -> Unit,
    onOpenActionPanel: (RaycastActionPanelSpec) -> Unit,
    onInvokeCallback: (String, Array<Any?>) -> Unit,
    externalSearchQuery: String? = null,
    hideSearchBar: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    fun execute(action: RaycastActionSpec) {
        executeRaycastAction(action, formState, onInvokeCallback)
    }

    when (screen) {
        is RaycastViewScreen.ListScreen -> RaycastListView(
            screen = screen,
            extension = extension,
            selectedItemId = selectedItemId,
            externalQuery = externalSearchQuery,
            hideSearchBar = hideSearchBar,
            contentPadding = contentPadding,
            onSelect = { item ->
                onSelectItem(item.id)
                screen.onSelectionChangeId?.let { onInvokeCallback(it, arrayOf(item.id)) }
            },
            onPrimaryAction = { item ->
                onSelectItem(item.id)
                val primary = item.actions?.primaryAction ?: screen.actions?.primaryAction
                if (primary != null) execute(primary)
                else if (item.actions != null) onOpenActionPanel(item.actions)
            },
            onLongPress = { item ->
                onSelectItem(item.id)
                (item.actions ?: screen.actions)?.let(onOpenActionPanel)
            },
            onSearchChange = { query ->
                screen.onSearchTextChangeId?.let { onInvokeCallback(it, arrayOf(query)) }
            },
            onDropdownChange = { dropdown, value ->
                dropdown.onChangeId?.let { onInvokeCallback(it, arrayOf(value)) }
            },
            onLoadMore = {
                screen.onLoadMoreId?.let { onInvokeCallback(it, emptyArray()) }
            },
        )
        is RaycastViewScreen.GridScreen -> RaycastGridView(
            screen = screen,
            extension = extension,
            externalQuery = externalSearchQuery,
            hideSearchBar = hideSearchBar,
            contentPadding = contentPadding,
            onPrimaryAction = { item ->
                onSelectItem(item.id)
                val primary = item.actions?.primaryAction ?: screen.actions?.primaryAction
                if (primary != null) execute(primary)
                else if (item.actions != null) onOpenActionPanel(item.actions)
            },
            onLongPress = { item ->
                onSelectItem(item.id)
                (item.actions ?: screen.actions)?.let(onOpenActionPanel)
            },
            onSearchChange = { query ->
                screen.onSearchTextChangeId?.let { onInvokeCallback(it, arrayOf(query)) }
            },
            onDropdownChange = { dropdown, value ->
                dropdown.onChangeId?.let { onInvokeCallback(it, arrayOf(value)) }
            },
        )
        is RaycastViewScreen.DetailScreen -> RaycastDetailView(
            markdown = screen.markdown,
            metadata = screen.metadata,
            extension = extension,
            contentPadding = contentPadding,
        )
        is RaycastViewScreen.FormScreen -> RaycastFormView(
            fields = screen.fields,
            formState = formState,
            contentPadding = contentPadding,
            onFieldChange = { field, newVal ->
                formState[field.id] = newVal
                field.onChangeId?.let { onInvokeCallback(it, arrayOf(newVal)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaycastActionPanelSheet(
    panel: RaycastActionPanelSpec,
    extension: InstalledRaycastExtension,
    onDismissRequest: () -> Unit,
    onExecuteAction: (RaycastActionSpec) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = panel.title?.takeIf { it.isNotBlank() } ?: "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            panel.sections.forEach { section ->
                if (!section.title.isNullOrBlank()) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                section.actions.forEach { action ->
                    RaycastActionRow(
                        action = action,
                        extension = extension,
                        onClick = { onExecuteAction(action) },
                    )
                }
            }
            panel.submenus.forEach { submenu ->
                Text(
                    text = submenu.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                submenu.actions.forEach { action ->
                    RaycastActionRow(
                        action = action,
                        extension = extension,
                        onClick = { onExecuteAction(action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RaycastActionRow(
    action: RaycastActionSpec,
    extension: InstalledRaycastExtension,
    onClick: () -> Unit,
) {
    val isDestructive = action.style.equals("destructive", ignoreCase = true)
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RaycastIconView(
                spec = action.icon,
                extension = extension,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RaycastListView(
    screen: RaycastViewScreen.ListScreen,
    extension: InstalledRaycastExtension,
    selectedItemId: String?,
    externalQuery: String? = null,
    hideSearchBar: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    onSelect: (RaycastListItemSpec) -> Unit,
    onPrimaryAction: (RaycastListItemSpec) -> Unit,
    onLongPress: (RaycastListItemSpec) -> Unit,
    onSearchChange: (String) -> Unit,
    onDropdownChange: (RaycastDropdownSpec, String) -> Unit,
    onLoadMore: () -> Unit,
) {
    var localQuery by remember { mutableStateOf(screen.searchText ?: "") }
    LaunchedEffect(screen.searchText) {
        if (screen.searchText != null && screen.searchText != localQuery) {
            localQuery = screen.searchText
        }
    }
    val activeQuery = externalQuery ?: localQuery

    val filteredSections = remember(screen.sections, activeQuery, screen.filtering) {
        val q = activeQuery.trim().lowercase()
        if (!screen.filtering || q.isEmpty()) screen.sections
        else screen.sections.mapNotNull { sec ->
            val items = sec.items.filter { item ->
                item.title.lowercase().contains(q) ||
                    (item.subtitle?.lowercase()?.contains(q) == true) ||
                    item.keywords.any { it.lowercase().contains(q) }
            }
            if (items.isEmpty()) null else sec.copy(items = items)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hideSearchBar) {
            RaycastSearchAndDropdownBar(
                query = activeQuery,
                placeholder = screen.searchBarPlaceholder ?: "Search...",
                dropdown = screen.dropdown,
                onQueryChange = {
                    localQuery = it
                    onSearchChange(it)
                },
                onDropdownChange = onDropdownChange,
            )
        }

        val totalItems = filteredSections.sumOf { it.items.size }
        if (totalItems == 0) {
            RaycastEmptyState(screen.emptyView, extension)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filteredSections.forEachIndexed { sectionIndex, section ->
                    if (!section.title.isNullOrBlank()) {
                        item(key = "header_${sectionIndex}_${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        items = section.items,
                        key = { itemIndex, item -> "item_${sectionIndex}_${itemIndex}_${item.id}" },
                    ) { _, item ->
                        val isSelected = item.id == selectedItemId
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onPrimaryAction(item) },
                                    onLongClick = { onLongPress(item) },
                                ),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            RaycastIconView(
                                                spec = item.icon,
                                                extension = extension,
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!item.subtitle.isNullOrBlank()) {
                                            Text(
                                                text = item.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    item.accessories.take(3).forEach { acc ->
                                        val isTag = !acc.tagText.isNullOrBlank()
                                        val pillText = acc.tagText ?: acc.text
                                        if (isTag || !pillText.isNullOrBlank() || acc.icon != null) {
                                            val tagColor = resolveRaycastColor(acc.tagColor)
                                                ?: resolveRaycastColor(acc.textColor)
                                                ?: if (isTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            if (isTag) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = tagColor.copy(alpha = 0.16f),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        if (acc.icon != null) {
                                                            RaycastIconView(acc.icon, extension, Modifier.size(12.dp), tagColor)
                                                        }
                                                        Text(
                                                            text = pillText.orEmpty(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = tagColor,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    if (acc.icon != null) {
                                                        RaycastIconView(acc.icon, extension, Modifier.size(14.dp), tagColor)
                                                    }
                                                    if (!pillText.isNullOrBlank()) {
                                                        Text(
                                                            text = pillText,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = tagColor,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (screen.isShowingDetail && (!item.detailMarkdown.isNullOrBlank() || item.detailMetadata.isNotEmpty())) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                    if (!item.detailMarkdown.isNullOrBlank()) {
                                        RaycastMarkdownBlock(item.detailMarkdown)
                                    }
                                    if (item.detailMetadata.isNotEmpty()) {
                                        if (!item.detailMarkdown.isNullOrBlank()) Spacer(Modifier.height(8.dp))
                                        RaycastMetadataBlock(item.detailMetadata, extension)
                                    }
                                }
                            }
                        }
                    }
                }
                if (screen.hasMore && screen.onLoadMoreId != null) {
                    item(key = "load_more") {
                        FilledTonalButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Load more")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RaycastGridView(
    screen: RaycastViewScreen.GridScreen,
    extension: InstalledRaycastExtension,
    externalQuery: String? = null,
    hideSearchBar: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onPrimaryAction: (RaycastGridItemSpec) -> Unit,
    onLongPress: (RaycastGridItemSpec) -> Unit,
    onSearchChange: (String) -> Unit,
    onDropdownChange: (RaycastDropdownSpec, String) -> Unit,
) {
    var localQuery by remember { mutableStateOf(screen.searchText ?: "") }
    LaunchedEffect(screen.searchText) {
        if (screen.searchText != null && screen.searchText != localQuery) {
            localQuery = screen.searchText
        }
    }
    val activeQuery = externalQuery ?: localQuery
    val filteredSections = remember(screen.sections, activeQuery, screen.filtering) {
        val q = activeQuery.trim().lowercase()
        if (!screen.filtering || q.isEmpty()) screen.sections
        else screen.sections.mapNotNull { sec ->
            val items = sec.items.filter { item ->
                (item.title?.lowercase()?.contains(q) == true) ||
                    (item.subtitle?.lowercase()?.contains(q) == true) ||
                    item.keywords.any { it.lowercase().contains(q) }
            }
            if (items.isEmpty()) null else sec.copy(items = items)
        }
    }

    val cellAspectRatio = remember(screen.aspectRatio) { parseAspectRatio(screen.aspectRatio) }
    val imageScale = if (screen.fit.equals("fill", ignoreCase = true)) ContentScale.Crop else ContentScale.Fit

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hideSearchBar) {
            RaycastSearchAndDropdownBar(
                query = activeQuery,
                placeholder = screen.searchBarPlaceholder ?: "Search grid...",
                dropdown = screen.dropdown,
                onQueryChange = {
                    localQuery = it
                    onSearchChange(it)
                },
                onDropdownChange = onDropdownChange,
            )
        }

        if (filteredSections.sumOf { it.items.size } == 0) {
            RaycastEmptyState(screen.emptyView, extension)
        } else {
            val cols = screen.columns.coerceIn(2, 5)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                filteredSections.forEachIndexed { sectionIndex, section ->
                    if (!section.title.isNullOrBlank()) {
                        item(
                            key = "grid_header_${sectionIndex}_${section.title}",
                            span = { GridItemSpan(cols) },
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    itemsIndexed(
                        items = section.items,
                        key = { itemIndex, item -> "grid_item_${sectionIndex}_${itemIndex}_${item.id}" },
                    ) { _, item ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onPrimaryAction(item) },
                                    onLongClick = { onLongPress(item) },
                                ),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                val fillColor = resolveRaycastColor(item.content?.colorFill)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(cellAspectRatio)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(fillColor ?: MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (fillColor == null) {
                                        RaycastIconView(
                                            spec = item.content,
                                            extension = extension,
                                            modifier = Modifier.fillMaxSize(),
                                            tint = MaterialTheme.colorScheme.primary,
                                            contentScale = imageScale,
                                        )
                                    }
                                }
                                if (!item.title.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!item.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = item.subtitle,
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
    }
}

private fun parseAspectRatio(raw: String): Float {
    val clean = raw.trim()
    if (clean.contains("/")) {
        val parts = clean.split("/")
        val w = parts.getOrNull(0)?.trim()?.toFloatOrNull()
        val h = parts.getOrNull(1)?.trim()?.toFloatOrNull()
        if (w != null && h != null && h > 0f && w > 0f) return (w / h).coerceIn(0.3f, 3f)
    }
    return clean.toFloatOrNull()?.coerceIn(0.3f, 3f) ?: 1f
}

@Composable
private fun RaycastDetailView(
    markdown: String,
    metadata: List<RaycastMetadataEntry>,
    extension: InstalledRaycastExtension,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RaycastMarkdownBlock(markdown)
        if (metadata.isNotEmpty()) {
            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RaycastMetadataBlock(metadata, extension)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RaycastFormView(
    fields: List<RaycastFormFieldSpec>,
    formState: MutableMap<String, Any>,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onFieldChange: (RaycastFormFieldSpec, Any) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        fields.forEach { field ->
            when (field.kind) {
                "separator" -> HorizontalDivider()
                "description" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!field.title.isNullOrBlank()) {
                        Text(field.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (!field.textValue.isNullOrBlank()) {
                        Text(field.textValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "checkbox" -> {
                    val checked = formState[field.id] as? Boolean ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onFieldChange(field, !checked) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onFieldChange(field, it) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(field.label ?: field.title ?: field.id, style = MaterialTheme.typography.bodyLarge)
                            if (!field.info.isNullOrBlank()) {
                                Text(field.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!field.error.isNullOrBlank()) {
                                Text(field.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                "dropdown" -> {
                    var expanded by remember(field.id) { mutableStateOf(false) }
                    val selectedVal = formState[field.id]?.toString() ?: ""
                    val selectedTitle = field.options.firstOrNull { it.value == selectedVal }?.title
                        ?: selectedVal.ifBlank { field.placeholder ?: "Select..." }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!field.title.isNullOrBlank()) {
                            Text(field.title, style = MaterialTheme.typography.labelMedium)
                        }
                        Box {
                            Surface(
                                onClick = { expanded = true },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(selectedTitle, modifier = Modifier.weight(1f))
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                field.options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.title) },
                                        onClick = {
                                            expanded = false
                                            onFieldChange(field, opt.value)
                                        },
                                    )
                                }
                            }
                        }
                        if (!field.error.isNullOrBlank()) {
                            Text(field.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (!field.info.isNullOrBlank()) {
                            Text(field.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                "tag-picker" -> {
                    @Suppress("UNCHECKED_CAST")
                    val selectedTags = (formState[field.id] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!field.title.isNullOrBlank()) {
                            Text(field.title, style = MaterialTheme.typography.labelMedium)
                        }
                        if (field.options.isEmpty()) {
                            Text(
                                text = selectedTags.joinToString(", ").ifBlank { field.placeholder ?: "No tags" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                field.options.forEach { opt ->
                                    val isSelected = selectedTags.contains(opt.value)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val updated = if (isSelected) {
                                                selectedTags.filterNot { it == opt.value }
                                            } else {
                                                selectedTags + opt.value
                                            }
                                            onFieldChange(field, updated)
                                        },
                                        label = { Text(opt.title) },
                                    )
                                }
                            }
                        }
                        if (!field.error.isNullOrBlank()) {
                            Text(field.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (!field.info.isNullOrBlank()) {
                            Text(field.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                "date-picker" -> {
                    var showDialog by remember(field.id) { mutableStateOf(false) }
                    val rawVal = formState[field.id]?.toString().orEmpty()
                    val parsedMillis = remember(rawVal) { parseIsoDateMillis(rawVal) }
                    val displayText = remember(rawVal, parsedMillis) {
                        if (parsedMillis != null) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(parsedMillis))
                        } else {
                            rawVal.ifBlank { field.placeholder ?: "Pick a date..." }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!field.title.isNullOrBlank()) {
                            Text(field.title, style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(
                            onClick = { showDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = displayText,
                                    modifier = Modifier.weight(1f),
                                    color = if (rawVal.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                if (rawVal.isNotBlank()) {
                                    IconButton(
                                        onClick = { onFieldChange(field, "") },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear date", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (!field.error.isNullOrBlank()) {
                            Text(field.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (!field.info.isNullOrBlank()) {
                            Text(field.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (showDialog) {
                        val pickerState = rememberDatePickerState(initialSelectedDateMillis = parsedMillis)
                        DatePickerDialog(
                            onDismissRequest = { showDialog = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDialog = false
                                    val ms = pickerState.selectedDateMillis
                                    if (ms != null) {
                                        val iso = SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US).apply {
                                            timeZone = TimeZone.getTimeZone("UTC")
                                        }.format(Date(ms))
                                        onFieldChange(field, iso)
                                    }
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = false }) {
                                    Text("Cancel")
                                }
                            },
                        ) {
                            DatePicker(state = pickerState)
                        }
                    }
                }
                else -> {
                    val value = formState[field.id]?.toString() ?: ""
                    val isMultiline = field.kind == "text-area"
                    val isPassword = field.kind == "password-field"
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onFieldChange(field, it) },
                        label = field.title?.let { { Text(it) } },
                        placeholder = field.placeholder?.let { { Text(it) } },
                        supportingText = (field.error ?: field.info)?.let { { Text(it) } },
                        isError = !field.error.isNullOrBlank(),
                        minLines = if (isMultiline) 4 else 1,
                        maxLines = if (isMultiline) 10 else 1,
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun parseIsoDateMillis(raw: String): Long? {
    if (raw.isBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
    )
    for (p in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(p, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return raw.toLongOrNull()
}

@Composable
private fun RaycastSearchAndDropdownBar(
    query: String,
    placeholder: String,
    dropdown: RaycastDropdownSpec?,
    onQueryChange: (String) -> Unit,
    onDropdownChange: (RaycastDropdownSpec, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (dropdown != null && dropdown.options.isNotEmpty()) {
            var selected by remember(dropdown.value, dropdown.defaultValue) {
                mutableStateOf(dropdown.value ?: dropdown.defaultValue ?: dropdown.options.first().value)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dropdown.options.forEach { opt ->
                    FilterChip(
                        selected = selected == opt.value,
                        onClick = {
                            selected = opt.value
                            onDropdownChange(dropdown, opt.value)
                        },
                        label = { Text(opt.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RaycastEmptyState(
    emptyView: RaycastEmptyViewSpec?,
    extension: InstalledRaycastExtension,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaycastIconView(
                spec = emptyView?.icon,
                extension = extension,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = emptyView?.title ?: "No results",
                style = MaterialTheme.typography.titleMedium,
            )
            val emptyDesc = emptyView?.description
            if (!emptyDesc.isNullOrBlank()) {
                Text(
                    text = emptyDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaycastMetadataBlock(
    entries: List<RaycastMetadataEntry>,
    extension: InstalledRaycastExtension,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            when (entry.type) {
                "separator" -> HorizontalDivider()
                "label" -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.title ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (entry.icon != null) {
                            RaycastIconView(entry.icon, extension, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = entry.text ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = resolveRaycastColor(entry.textColor) ?: Color.Unspecified,
                        )
                    }
                }
                "link" -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !entry.target.isNullOrBlank()) {
                            entry.target?.let { runCatching { uriHandler.openUri(it) } }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entry.title ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.text ?: entry.target ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                "tag-list" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!entry.title.isNullOrBlank()) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entry.tags.forEach { (label, colorLike) ->
                            val c = resolveRaycastColor(colorLike) ?: MaterialTheme.colorScheme.primary
                            Surface(shape = CircleShape, color = c.copy(alpha = 0.16f)) {
                                Text(
                                    text = label,
                                    color = c,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RaycastMarkdownBlock(markdown: String) {
    val lines = remember(markdown) { markdown.lines() }
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    var inCode = false
    val codeBuf = StringBuilder()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.trim().startsWith("```")) {
                if (!inCode) {
                    inCode = true
                    codeBuf.clear()
                } else {
                    inCode = false
                    val block = codeBuf.toString().trimEnd()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = codeBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                continue
            }
            if (inCode) {
                codeBuf.appendLine(rawLine)
                continue
            }
            val imgMatch = Regex("^!\\[([^\\]]*)\\]\\(([^)]+)\\)$").matchEntire(line.trim())
            when {
                imgMatch != null -> {
                    val imgUrl = imgMatch.groupValues[2].trim().substringBefore(" ")
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = imgMatch.groupValues[1].ifBlank { null },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                line.startsWith("#### ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("#### "), linkColor, codeBg, uriHandler),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("### ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("### "), linkColor, codeBg, uriHandler),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("## ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("## "), linkColor, codeBg, uriHandler),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("# ") -> Text(
                    text = parseInlineMarkdown(line.removePrefix("# "), linkColor, codeBg, uriHandler),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("> ") -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = parseInlineMarkdown(line.removePrefix("> "), linkColor, codeBg, uriHandler),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                line.trim() == "---" || line.trim() == "***" -> HorizontalDivider()
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val trimmed = line.trimStart().drop(2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = parseInlineMarkdown(trimmed, linkColor, codeBg, uriHandler),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Regex("^\\s*\\d+\\.\\s+.*").matches(line) -> {
                    val trimmed = line.trimStart()
                    val dotIdx = trimmed.indexOf('.')
                    val num = trimmed.substring(0, dotIdx + 1)
                    val rest = trimmed.substring(dotIdx + 1).trimStart()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(num, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = parseInlineMarkdown(rest, linkColor, codeBg, uriHandler),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                line.trim().startsWith("|") && line.trim().endsWith("|") -> {
                    val cells = line.trim().trim('|').split('|').map { it.trim() }
                    val isDivider = cells.all { c -> c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' } }
                    if (!isDivider) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            cells.forEach { cell ->
                                Text(
                                    text = parseInlineMarkdown(cell, linkColor, codeBg, uriHandler),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                line.isNotBlank() -> Text(
                    text = parseInlineMarkdown(line, linkColor, codeBg, uriHandler),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun parseInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBg: Color,
    uriHandler: androidx.compose.ui.platform.UriHandler,
): AnnotatedString {
    val tokenRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|__[^_]+__|~~[^~]+~~|\\*[^*]+\\*|\\[[^\\]]+\\]\\([^)]+\\))")
    return buildAnnotatedString {
        var idx = 0
        for (m in tokenRegex.findAll(text)) {
            if (m.range.first > idx) {
                append(text.substring(idx, m.range.first))
            }
            val tok = m.value
            when {
                tok.startsWith("`") && tok.endsWith("`") -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(tok.substring(1, tok.length - 1))
                    }
                }
                (tok.startsWith("**") && tok.endsWith("**")) || (tok.startsWith("__") && tok.endsWith("__")) -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(tok.substring(2, tok.length - 2))
                    }
                }
                tok.startsWith("~~") && tok.endsWith("~~") -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(tok.substring(2, tok.length - 2))
                    }
                }
                tok.startsWith("*") && tok.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(tok.substring(1, tok.length - 1))
                    }
                }
                tok.startsWith("[") -> {
                    val closeBracket = tok.indexOf("](")
                    if (closeBracket > 0 && tok.endsWith(")")) {
                        val label = tok.substring(1, closeBracket)
                        val url = tok.substring(closeBracket + 2, tok.length - 1)
                        pushLink(
                            LinkAnnotation.Url(
                                url = url,
                                linkInteractionListener = {
                                    runCatching { uriHandler.openUri(url) }
                                },
                            ),
                        )
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
                        pop()
                    } else {
                        append(tok)
                    }
                }
                else -> append(tok)
            }
            idx = m.range.last + 1
        }
        if (idx < text.length) {
            append(text.substring(idx))
        }
    }
}

@Composable
fun RaycastIconView(
    spec: RaycastImageSpec?,
    extension: InstalledRaycastExtension,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val isDark = isSystemInDarkTheme()
    val tintResolved = resolveRaycastColor(spec?.tintColor, isDark) ?: tint
    val fileIcon = spec?.fileIcon
    if (!fileIcon.isNullOrBlank()) {
        val looksLikeDir = !fileIcon.substringAfterLast('/').contains('.')
        Icon(
            imageVector = if (looksLikeDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            tint = tintResolved,
            modifier = modifier,
        )
        return
    }
    val source = (if (isDark) spec?.darkSource else spec?.lightSource)
        ?: spec?.rawSource
        ?: spec?.darkSource
        ?: spec?.lightSource
        ?: spec?.fallbackSource
    val shape = if (spec?.mask == "Circle") CircleShape else RoundedCornerShape(8.dp)
    if (source != null) {
        when {
            source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:") || source.startsWith("file://") -> {
                AsyncImage(
                    model = source,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = modifier.clip(shape),
                )
                return
            }
            source.startsWith("/") -> {
                val localFile = File(source)
                if (localFile.exists()) {
                    AsyncImage(
                        model = localFile,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = modifier.clip(shape),
                    )
                    return
                }
            }
            source.startsWith("icon://") -> {
                val iconName = source.removePrefix("icon://")
                Icon(
                    imageVector = mapRaycastIcon(iconName),
                    contentDescription = null,
                    tint = tintResolved,
                    modifier = modifier,
                )
                return
            }
            else -> {
                val assetFile = File(extension.dirPath, "assets/$source").takeIf { it.exists() }
                    ?: File(extension.dirPath, source).takeIf { it.exists() }
                if (assetFile != null) {
                    AsyncImage(
                        model = assetFile,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = modifier.clip(shape),
                    )
                    return
                }
            }
        }
    }
    Icon(
        imageVector = Icons.Rounded.Extension,
        contentDescription = null,
        tint = tintResolved,
        modifier = modifier,
    )
}

fun resolveRaycastColor(colorLike: RaycastColorLike?): Color? = resolveRaycastColor(colorLike, isDark = true)

fun resolveRaycastColor(colorLike: RaycastColorLike?, isDark: Boolean): Color? {
    val raw = (if (isDark) colorLike?.dark else colorLike?.light)
        ?: colorLike?.raw
        ?: colorLike?.dark
        ?: colorLike?.light
        ?: return null
    val clean = raw.trim()
    return when (clean.lowercase()) {
        "red" -> Color(0xFFFF6369)
        "orange" -> Color(0xFFFF9F0A)
        "yellow" -> Color(0xFFFFD60A)
        "green" -> Color(0xFF30D158)
        "blue" -> Color(0xFF0A84FF)
        "purple" -> Color(0xFFBF5AF2)
        "magenta" -> Color(0xFFFF375F)
        "primarytext" -> if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
        "secondarytext" -> Color(0xFF8E8E93)
        else -> runCatching {
            when {
                clean.startsWith("#") && clean.length == 4 -> {
                    val r = clean[1]
                    val g = clean[2]
                    val b = clean[3]
                    Color(android.graphics.Color.parseColor("#$r$r$g$g$b$b"))
                }
                clean.startsWith("#") -> Color(android.graphics.Color.parseColor(clean))
                clean.startsWith("rgb(", ignoreCase = true) || clean.startsWith("rgba(", ignoreCase = true) -> {
                    val nums = clean.substringAfter("(").substringBefore(")").split(",").map { it.trim().toFloat() }
                    if (nums.size >= 3) {
                        val r = nums[0].toInt().coerceIn(0, 255)
                        val g = nums[1].toInt().coerceIn(0, 255)
                        val b = nums[2].toInt().coerceIn(0, 255)
                        val a = if (nums.size >= 4) (nums[3].coerceIn(0f, 1f) * 255f).toInt() else 255
                        Color(red = r, green = g, blue = b, alpha = a)
                    } else null
                }
                else -> null
            }
        }.getOrNull()
    }
}

private fun mapRaycastIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "plus", "plus-circle", "plus-square", "add-person" -> Icons.Rounded.Add
        "magnifying-glass", "binoculars" -> Icons.Rounded.Search
        "clipboard", "copy-clipboard" -> Icons.Rounded.ContentCopy
        "globe", "compass", "network" -> Icons.Rounded.Public
        "trash" -> Icons.Rounded.Delete
        "check", "checkmark" -> Icons.Rounded.Check
        "check-circle" -> Icons.Rounded.CheckCircle
        "xmark", "xmark-circle", "multiply", "minus", "minus-circle" -> Icons.Rounded.Close
        "star", "star-circle", "stars" -> Icons.Rounded.Star
        "heart", "heart-disabled" -> Icons.Rounded.Favorite
        "bookmark" -> Icons.Rounded.Bookmark
        "document", "new-document", "save-document", "book", "blank-document", "text" -> Icons.Rounded.Description
        "folder", "new-folder", "finder" -> Icons.Rounded.Folder
        "code", "code-block", "curly-braces" -> Icons.Rounded.Code
        "terminal" -> Icons.Rounded.Terminal
        "gear", "cog", "wrench-screwdriver" -> Icons.Rounded.Settings
        "hammer" -> Icons.Rounded.Build
        "lock" -> Icons.Rounded.Lock
        "lock-unlocked", "lock-disabled" -> Icons.Rounded.LockOpen
        "key" -> Icons.Rounded.Key
        "eye" -> Icons.Rounded.Visibility
        "eye-disabled" -> Icons.Rounded.VisibilityOff
        "pencil", "edit-shape" -> Icons.Rounded.Edit
        "envelope" -> Icons.Rounded.Email
        "calendar" -> Icons.Rounded.CalendarToday
        "clock", "alarm", "stopwatch" -> Icons.Rounded.Schedule
        "hourglass" -> Icons.Rounded.Timer
        "person", "person-circle", "two-people", "person-lines" -> Icons.Rounded.Person
        "link" -> Icons.Rounded.Link
        "image" -> Icons.Rounded.Image
        "camera" -> Icons.Rounded.CameraAlt
        "video" -> Icons.Rounded.Videocam
        "music", "headphones" -> Icons.Rounded.MusicNote
        "microphone" -> Icons.Rounded.Mic
        "speaker-up", "speaker-on" -> Icons.AutoMirrored.Rounded.VolumeUp
        "cloud", "cloud-lightning", "cloud-rain", "cloud-sun" -> Icons.Rounded.Cloud
        "download" -> Icons.Rounded.Download
        "upload" -> Icons.Rounded.Upload
        "play" -> Icons.Rounded.PlayArrow
        "pause" -> Icons.Rounded.Pause
        "stop" -> Icons.Rounded.Stop
        "arrow-clockwise", "rotate-clockwise" -> Icons.Rounded.Refresh
        "arrow-right", "arrow-right-circle" -> Icons.AutoMirrored.Rounded.ArrowForward
        "arrow-left", "arrow-left-circle" -> Icons.AutoMirrored.Rounded.ArrowBack
        "arrow-up", "arrow-up-circle", "chevron-up" -> Icons.Rounded.ArrowUpward
        "arrow-down", "arrow-down-circle", "chevron-down" -> Icons.Rounded.ArrowDownward
        "chevron-left" -> Icons.Rounded.ChevronLeft
        "chevron-right" -> Icons.Rounded.ChevronRight
        "list", "bullet-points" -> Icons.AutoMirrored.Rounded.List
        "app-window", "window", "app-window-grid-2x2", "app-window-sidebar-left" -> Icons.AutoMirrored.Rounded.OpenInNew
        "bolt" -> Icons.Rounded.Bolt
        "info" -> Icons.Rounded.Info
        "warning", "exclamation-mark" -> Icons.Rounded.Warning
        "xmark-octagon", "bug" -> Icons.Rounded.BugReport
        "question-mark", "question-mark-circle" -> Icons.AutoMirrored.Rounded.HelpOutline
        "store" -> Icons.Rounded.Apps
        "tag", "hashtag" -> Icons.Rounded.Tag
        "pin" -> Icons.Rounded.PushPin
        "flag" -> Icons.Rounded.Flag
        "bell", "bell-disabled" -> Icons.Rounded.Notifications
        "house" -> Icons.Rounded.Home
        "desktop", "monitor" -> Icons.Rounded.Computer
        "phone" -> Icons.Rounded.Phone
        "keyboard" -> Icons.Rounded.Keyboard
        "printer" -> Icons.Rounded.Print
        "wifi", "signal-3" -> Icons.Rounded.Wifi
        "moon" -> Icons.Rounded.DarkMode
        "sun" -> Icons.Rounded.LightMode
        "brush", "swatch" -> Icons.Rounded.Palette
        "wand" -> Icons.Rounded.AutoAwesome
        "bar-chart", "line-chart" -> Icons.Rounded.BarChart
        "calculator" -> Icons.Rounded.Calculate
        "cart" -> Icons.Rounded.ShoppingCart
        "paperclip" -> Icons.Rounded.AttachFile
        "box", "tray" -> Icons.Rounded.Archive
        "hard-drive", "memory-stick" -> Icons.Rounded.Storage
        "circle", "circle-filled", "dot", "circle-progress", "circle-progress-25",
        "circle-progress-50", "circle-progress-75", "circle-progress-100" -> Icons.Rounded.Circle
        "filter" -> Icons.Rounded.FilterList
        "layers" -> Icons.Rounded.Layers
        "emoji" -> Icons.Rounded.EmojiEmotions
        "thumbs-up" -> Icons.Rounded.ThumbUp
        "power" -> Icons.Rounded.PowerSettingsNew
        "qr-code" -> Icons.Rounded.QrCode
        "repeat" -> Icons.Rounded.Sync
        "share" -> Icons.Rounded.Share
        "send" -> Icons.AutoMirrored.Rounded.Send
        "scissors" -> Icons.Rounded.ContentCut
        "paste" -> Icons.Rounded.ContentPaste
        "sliders" -> Icons.Rounded.Tune
        else -> Icons.Rounded.Extension
    }
}
