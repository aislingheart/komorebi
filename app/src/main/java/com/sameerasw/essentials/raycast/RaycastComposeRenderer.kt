package com.sameerasw.essentials.raycast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

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
    var selectedItemId by remember(screen) { mutableStateOf<String?>(null) }

    val primaryPanel: RaycastActionPanelSpec? = when (screen) {
        is RaycastViewScreen.ListScreen -> {
            val allItems = screen.sections.flatMap { it.items }
            val focused = allItems.firstOrNull { it.id == selectedItemId } ?: allItems.firstOrNull()
            focused?.actions ?: screen.actions
        }
        is RaycastViewScreen.GridScreen -> {
            val allItems = screen.sections.flatMap { it.items }
            val focused = allItems.firstOrNull { it.id == selectedItemId } ?: allItems.firstOrNull()
            focused?.actions ?: screen.actions
        }
        is RaycastViewScreen.DetailScreen -> screen.actions
        is RaycastViewScreen.FormScreen -> screen.actions
    }

    val formState = remember(screen) { mutableStateMapOf<String, Any>() }
    if (screen is RaycastViewScreen.FormScreen) {
        screen.fields.forEach { field ->
            if (!formState.containsKey(field.id)) {
                when (field.kind) {
                    "checkbox" -> formState[field.id] = field.boolValue ?: false
                    else -> formState[field.id] = field.textValue ?: ""
                }
            }
        }
    }

    fun executeAction(action: RaycastActionSpec) {
        if (action.isSubmit && action.onSubmitId != null) {
            onInvokeCallback(action.onSubmitId, arrayOf(formState.toMap()))
        } else if (action.onActionId != null) {
            onInvokeCallback(action.onActionId, emptyArray())
        }
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
            when (screen) {
                is RaycastViewScreen.ListScreen -> RaycastListView(
                    screen = screen,
                    extension = extension,
                    selectedItemId = selectedItemId,
                    onSelect = { item ->
                        selectedItemId = item.id
                        screen.onSelectionChangeId?.let { onInvokeCallback(it, arrayOf(item.id)) }
                    },
                    onPrimaryAction = { item ->
                        selectedItemId = item.id
                        val primary = item.actions?.primaryAction ?: screen.actions?.primaryAction
                        if (primary != null) executeAction(primary)
                        else if (item.actions != null) activeActionPanel = item.actions
                    },
                    onLongPress = { item ->
                        selectedItemId = item.id
                        activeActionPanel = item.actions ?: screen.actions
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
                    onPrimaryAction = { item ->
                        selectedItemId = item.id
                        val primary = item.actions?.primaryAction ?: screen.actions?.primaryAction
                        if (primary != null) executeAction(primary)
                        else if (item.actions != null) activeActionPanel = item.actions
                    },
                    onLongPress = { item ->
                        selectedItemId = item.id
                        activeActionPanel = item.actions ?: screen.actions
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
                )
                is RaycastViewScreen.FormScreen -> RaycastFormView(
                    fields = screen.fields,
                    formState = formState,
                    onFieldChange = { field, newVal ->
                        formState[field.id] = newVal
                        field.onChangeId?.let { onInvokeCallback(it, arrayOf(newVal)) }
                    },
                )
            }
        }
    }

    activeActionPanel?.let { panel ->
        ModalBottomSheet(
            onDismissRequest = { activeActionPanel = null },
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
                            onClick = {
                                activeActionPanel = null
                                executeAction(action)
                            },
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
                            onClick = {
                                activeActionPanel = null
                                executeAction(action)
                            },
                        )
                    }
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
    onSelect: (RaycastListItemSpec) -> Unit,
    onPrimaryAction: (RaycastListItemSpec) -> Unit,
    onLongPress: (RaycastListItemSpec) -> Unit,
    onSearchChange: (String) -> Unit,
    onDropdownChange: (RaycastDropdownSpec, String) -> Unit,
    onLoadMore: () -> Unit,
) {
    var localQuery by remember(screen.searchText) { mutableStateOf(screen.searchText ?: "") }

    val filteredSections = remember(screen.sections, localQuery, screen.filtering) {
        val q = localQuery.trim().lowercase()
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
        RaycastSearchAndDropdownBar(
            query = localQuery,
            placeholder = screen.searchBarPlaceholder ?: "Search...",
            dropdown = screen.dropdown,
            onQueryChange = {
                localQuery = it
                onSearchChange(it)
            },
            onDropdownChange = onDropdownChange,
        )

        val totalItems = filteredSections.sumOf { it.items.size }
        if (totalItems == 0) {
            RaycastEmptyState(screen.emptyView, extension)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filteredSections.forEach { section ->
                    if (!section.title.isNullOrBlank()) {
                        item(key = "header_${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(section.items, key = { it.id }) { item ->
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
                                    item.accessories.take(2).forEach { acc ->
                                        val pillText = acc.tagText ?: acc.text
                                        if (!pillText.isNullOrBlank()) {
                                            val tagColor = resolveRaycastColor(acc.tagColor)
                                                ?: MaterialTheme.colorScheme.primary
                                            Surface(
                                                shape = CircleShape,
                                                color = tagColor.copy(alpha = 0.16f),
                                            ) {
                                                Text(
                                                    text = pillText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tagColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (screen.isShowingDetail && !item.detailMarkdown.isNullOrBlank()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                    RaycastMarkdownBlock(item.detailMarkdown)
                                    if (item.detailMetadata.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
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
    onPrimaryAction: (RaycastGridItemSpec) -> Unit,
    onLongPress: (RaycastGridItemSpec) -> Unit,
    onSearchChange: (String) -> Unit,
    onDropdownChange: (RaycastDropdownSpec, String) -> Unit,
) {
    var localQuery by remember(screen.searchText) { mutableStateOf(screen.searchText ?: "") }
    val filteredSections = remember(screen.sections, localQuery, screen.filtering) {
        val q = localQuery.trim().lowercase()
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

    Column(modifier = Modifier.fillMaxSize()) {
        RaycastSearchAndDropdownBar(
            query = localQuery,
            placeholder = screen.searchBarPlaceholder ?: "Search grid...",
            dropdown = screen.dropdown,
            onQueryChange = {
                localQuery = it
                onSearchChange(it)
            },
            onDropdownChange = onDropdownChange,
        )

        if (filteredSections.sumOf { it.items.size } == 0) {
            RaycastEmptyState(screen.emptyView, extension)
        } else {
            val cols = screen.columns.coerceIn(2, 5)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                filteredSections.forEach { section ->
                    if (!section.title.isNullOrBlank()) {
                        item(span = { GridItemSpan(cols) }) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(section.items, key = { it.id }) { item ->
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
                                        .aspectRatio(1f)
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

@Composable
private fun RaycastDetailView(
    markdown: String,
    metadata: List<RaycastMetadataEntry>,
    extension: InstalledRaycastExtension,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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

@Composable
private fun RaycastFormView(
    fields: List<RaycastFormFieldSpec>,
    formState: MutableMap<String, Any>,
    onFieldChange: (RaycastFormFieldSpec, Any) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                        }
                    }
                }
                "dropdown" -> {
                    var expanded by remember { mutableStateOf(false) }
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
            if (!emptyView?.description.isNullOrBlank()) {
                Text(
                    text = emptyView?.description ?: "",
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
                        Text(entry.text ?: "", style = MaterialTheme.typography.bodyMedium)
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
    var inCode = false
    val codeBuf = StringBuilder()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                if (!inCode) {
                    inCode = true
                    codeBuf.clear()
                } else {
                    inCode = false
                    val block = codeBuf.toString().trimEnd()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                codeBuf.appendLine(line)
                continue
            }
            when {
                line.startsWith("### ") -> Text(
                    text = line.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("> ") -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = line.removePrefix("> "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                line.trim() == "---" || line.trim() == "***" -> HorizontalDivider()
                line.isNotBlank() -> Text(
                    text = line.replace("**", "").replace("__", ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun RaycastIconView(
    spec: RaycastImageSpec?,
    extension: InstalledRaycastExtension,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val source = spec?.darkSource ?: spec?.rawSource ?: spec?.lightSource
    if (source != null) {
        when {
            source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:") || source.startsWith("file://") -> {
                AsyncImage(
                    model = source,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.clip(
                        if (spec?.mask == "Circle") CircleShape else RoundedCornerShape(8.dp),
                    ),
                )
                return
            }
            source.startsWith("icon://") -> {
                val iconName = source.removePrefix("icon://")
                val resolvedTint = resolveRaycastColor(spec?.tintColor) ?: tint
                Icon(
                    imageVector = mapRaycastIcon(iconName),
                    contentDescription = null,
                    tint = resolvedTint,
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
                        contentScale = ContentScale.Crop,
                        modifier = modifier.clip(RoundedCornerShape(8.dp)),
                    )
                    return
                }
            }
        }
    }
    Icon(
        imageVector = Icons.Rounded.Extension,
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

fun resolveRaycastColor(colorLike: RaycastColorLike?): Color? {
    val hex = colorLike?.dark ?: colorLike?.raw ?: colorLike?.light ?: return null
    return runCatching {
        val clean = hex.trim()
        if (clean.startsWith("#")) Color(android.graphics.Color.parseColor(clean)) else null
    }.getOrNull()
}

private fun mapRaycastIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "plus", "plus-circle" -> Icons.Rounded.Add
        "magnifying-glass", "binoculars" -> Icons.Rounded.Search
        "clipboard", "copy-clipboard" -> Icons.Rounded.ContentCopy
        "globe", "compass", "network" -> Icons.Rounded.Public
        "trash" -> Icons.Rounded.Delete
        "check", "check-circle", "checkmark" -> Icons.Rounded.Check
        "xmark", "xmark-circle" -> Icons.Rounded.Close
        "star", "star-circle" -> Icons.Rounded.Star
        "heart" -> Icons.Rounded.Favorite
        "bookmark" -> Icons.Rounded.Bookmark
        "document", "new-document", "save-document", "book" -> Icons.Rounded.Description
        "folder", "finder" -> Icons.Rounded.Folder
        "code", "code-block" -> Icons.Rounded.Code
        "terminal" -> Icons.Rounded.Terminal
        "gear", "cog", "wrench-screwdriver" -> Icons.Rounded.Settings
        "lock", "lock-unlocked" -> Icons.Rounded.Lock
        "key" -> Icons.Rounded.Key
        "eye", "eye-disabled" -> Icons.Rounded.Visibility
        "pencil", "edit-shape" -> Icons.Rounded.Edit
        "envelope" -> Icons.Rounded.Email
        "calendar" -> Icons.Rounded.CalendarToday
        "person", "person-circle", "two-people" -> Icons.Rounded.Person
        "link" -> Icons.Rounded.Link
        "image" -> Icons.Rounded.Image
        "cloud" -> Icons.Rounded.Cloud
        "download" -> Icons.Rounded.Download
        "play" -> Icons.Rounded.PlayArrow
        "arrow-clockwise" -> Icons.Rounded.Refresh
        "arrow-right" -> Icons.AutoMirrored.Rounded.ArrowForward
        "list", "bullet-points" -> Icons.AutoMirrored.Rounded.List
        "app-window", "window" -> Icons.AutoMirrored.Rounded.OpenInNew
        "bolt" -> Icons.Rounded.Bolt
        "info" -> Icons.Rounded.Info
        "warning", "exclamation-mark" -> Icons.Rounded.Warning
        "store" -> Icons.Rounded.Apps
        else -> Icons.Rounded.Extension
    }
}
