package com.sameerasw.essentials.raycast

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class RaycastColorLike(
    val raw: String? = null,
    val light: String? = null,
    val dark: String? = null,
)

data class RaycastImageSpec(
    val rawSource: String? = null,
    val lightSource: String? = null,
    val darkSource: String? = null,
    val mask: String? = null,
    val tintColor: RaycastColorLike? = null,
    val colorFill: RaycastColorLike? = null,
    val fileIcon: String? = null,
    val fallbackSource: String? = null,
)

data class RaycastActionSpec(
    val title: String,
    val icon: RaycastImageSpec? = null,
    val style: String = "regular",
    val onActionId: String? = null,
    val onSubmitId: String? = null,
    val isSubmit: Boolean = false,
)

data class RaycastActionSubmenu(
    val title: String,
    val icon: RaycastImageSpec? = null,
    val onOpenId: String? = null,
    val actions: List<RaycastActionSpec> = emptyList(),
)

data class RaycastActionSection(
    val title: String? = null,
    val actions: List<RaycastActionSpec> = emptyList(),
)

data class RaycastActionPanelSpec(
    val title: String? = null,
    val sections: List<RaycastActionSection> = emptyList(),
    val submenus: List<RaycastActionSubmenu> = emptyList(),
) {
    val primaryAction: RaycastActionSpec?
        get() = sections.firstOrNull { it.actions.isNotEmpty() }?.actions?.firstOrNull()
}

data class RaycastAccessorySpec(
    val text: String? = null,
    val tagText: String? = null,
    val tagColor: RaycastColorLike? = null,
    val icon: RaycastImageSpec? = null,
    val textColor: RaycastColorLike? = null,
)

data class RaycastMetadataEntry(
    val type: String, // "label", "link", "separator", "tag-list"
    val title: String? = null,
    val text: String? = null,
    val target: String? = null,
    val icon: RaycastImageSpec? = null,
    val tags: List<Pair<String, RaycastColorLike?>> = emptyList(),
    val textColor: RaycastColorLike? = null,
)

data class RaycastDropdownOption(
    val title: String,
    val value: String,
    val sectionTitle: String? = null,
)

data class RaycastDropdownSpec(
    val tooltip: String? = null,
    val placeholder: String? = null,
    val value: String? = null,
    val defaultValue: String? = null,
    val onChangeId: String? = null,
    val options: List<RaycastDropdownOption> = emptyList(),
)

data class RaycastEmptyViewSpec(
    val title: String = "No Results",
    val description: String? = null,
    val icon: RaycastImageSpec? = null,
    val actions: RaycastActionPanelSpec? = null,
)

data class RaycastListItemSpec(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: RaycastImageSpec? = null,
    val keywords: List<String> = emptyList(),
    val accessories: List<RaycastAccessorySpec> = emptyList(),
    val detailMarkdown: String? = null,
    val detailMetadata: List<RaycastMetadataEntry> = emptyList(),
    val actions: RaycastActionPanelSpec? = null,
)

data class RaycastListSectionSpec(
    val title: String? = null,
    val subtitle: String? = null,
    val items: List<RaycastListItemSpec> = emptyList(),
)

data class RaycastGridItemSpec(
    val id: String,
    val title: String? = null,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val content: RaycastImageSpec? = null,
    val actions: RaycastActionPanelSpec? = null,
)

data class RaycastGridSectionSpec(
    val title: String? = null,
    val subtitle: String? = null,
    val columns: Int? = null,
    val items: List<RaycastGridItemSpec> = emptyList(),
)

data class RaycastFormFieldSpec(
    val kind: String, // text-field, password-field, text-area, checkbox, dropdown, date-picker, tag-picker, separator, description
    val id: String,
    val title: String? = null,
    val placeholder: String? = null,
    val info: String? = null,
    val error: String? = null,
    val label: String? = null,
    val textValue: String? = null,
    val boolValue: Boolean? = null,
    val onChangeId: String? = null,
    val options: List<RaycastDropdownOption> = emptyList(),
    val hasControlledValue: Boolean = false,
    val tagValues: List<String> = emptyList(),
    val isFullDay: Boolean = false,
)

sealed interface RaycastViewScreen {
    val navigationTitle: String?
    val isLoading: Boolean
    val actions: RaycastActionPanelSpec?

    data class ListScreen(
        override val navigationTitle: String?,
        override val isLoading: Boolean,
        override val actions: RaycastActionPanelSpec?,
        val searchText: String?,
        val searchBarPlaceholder: String?,
        val filtering: Boolean,
        val isShowingDetail: Boolean,
        val onSearchTextChangeId: String?,
        val onSelectionChangeId: String?,
        val onLoadMoreId: String?,
        val hasMore: Boolean,
        val dropdown: RaycastDropdownSpec?,
        val emptyView: RaycastEmptyViewSpec?,
        val sections: List<RaycastListSectionSpec>,
        val selectedItemId: String? = null,
    ) : RaycastViewScreen

    data class GridScreen(
        override val navigationTitle: String?,
        override val isLoading: Boolean,
        override val actions: RaycastActionPanelSpec?,
        val columns: Int,
        val aspectRatio: String,
        val fit: String,
        val searchText: String?,
        val searchBarPlaceholder: String?,
        val filtering: Boolean,
        val onSearchTextChangeId: String?,
        val dropdown: RaycastDropdownSpec?,
        val emptyView: RaycastEmptyViewSpec?,
        val sections: List<RaycastGridSectionSpec>,
        val itemSize: String? = null,
    ) : RaycastViewScreen

    data class DetailScreen(
        override val navigationTitle: String?,
        override val isLoading: Boolean,
        override val actions: RaycastActionPanelSpec?,
        val markdown: String,
        val metadata: List<RaycastMetadataEntry>,
    ) : RaycastViewScreen

    data class FormScreen(
        override val navigationTitle: String?,
        override val isLoading: Boolean,
        override val actions: RaycastActionPanelSpec?,
        val fields: List<RaycastFormFieldSpec>,
    ) : RaycastViewScreen
}

object RaycastTreeParser {
    private val gson = Gson()

    fun parseViewStack(treeJson: String): List<RaycastViewScreen> {
        val root = runCatching { gson.fromJson(treeJson, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val views = root.getAsJsonArray("children") ?: return emptyList()
        val out = mutableListOf<RaycastViewScreen>()
        for (el in views) {
            val viewObj = el.asJsonObjectOrNull() ?: continue
            val contentNode = if (viewObj.str("\$t") == "view") {
                viewObj.children().firstOrNull()
            } else {
                viewObj
            }
            if (contentNode != null) {
                parseScreen(contentNode)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun parseScreen(obj: JsonObject): RaycastViewScreen? {
        return when (obj.str("\$t")) {
            "list" -> parseList(obj)
            "grid" -> parseGrid(obj)
            "detail" -> parseDetail(obj)
            "form" -> parseForm(obj)
            else -> null
        }
    }

    private fun parseList(obj: JsonObject): RaycastViewScreen.ListScreen {
        var dropdown: RaycastDropdownSpec? = null
        var actions: RaycastActionPanelSpec? = null
        var emptyView: RaycastEmptyViewSpec? = null
        val sections = mutableListOf<RaycastListSectionSpec>()
        val looseItems = mutableListOf<RaycastListItemSpec>()

        for (child in obj.children()) {
            when (child.str("\$t")) {
                "dropdown" -> dropdown = parseDropdown(child)
                "action-panel" -> actions = parseActionPanel(child)
                "empty-view" -> emptyView = parseEmptyView(child)
                "list-section" -> {
                    val items = child.children().mapIndexedNotNull { idx, c ->
                        if (c.str("\$t") == "list-item") parseListItem(c, "sec_${sections.size}_$idx") else null
                    }
                    sections.add(
                        RaycastListSectionSpec(
                            title = child.str("title"),
                            subtitle = child.str("subtitle"),
                            items = items,
                        ),
                    )
                }
                "list-item" -> looseItems.add(parseListItem(child, "loose_${looseItems.size}"))
            }
        }
        if (looseItems.isNotEmpty()) {
            sections.add(0, RaycastListSectionSpec(title = null, items = looseItems))
        }

        return RaycastViewScreen.ListScreen(
            navigationTitle = obj.str("navigationTitle"),
            isLoading = obj.bool("isLoading", false),
            actions = actions,
            searchText = obj.str("searchText"),
            searchBarPlaceholder = obj.str("searchBarPlaceholder"),
            filtering = obj.bool("filtering", true),
            isShowingDetail = obj.bool("isShowingDetail", false),
            onSearchTextChangeId = obj.str("onSearchTextChange"),
            onSelectionChangeId = obj.str("onSelectionChange"),
            onLoadMoreId = obj.str("onLoadMore"),
            hasMore = obj.bool("hasMore", false),
            dropdown = dropdown,
            emptyView = emptyView,
            sections = sections,
            selectedItemId = obj.str("selectedItemId"),
        )
    }

    private fun parseListItem(obj: JsonObject, fallbackId: String): RaycastListItemSpec {
        var actions: RaycastActionPanelSpec? = null
        var detailMarkdown: String? = null
        var detailMeta: List<RaycastMetadataEntry> = emptyList()
        for (child in obj.children()) {
            when (child.str("\$t")) {
                "action-panel" -> actions = parseActionPanel(child)
                "list-item-detail" -> {
                    detailMarkdown = child.str("markdown")
                    val metaObj = child.children().firstOrNull { it.str("\$t") == "list-item-detail-metadata" }
                    if (metaObj != null) detailMeta = parseMetadata(metaObj)
                }
            }
        }
        val accessories = obj.getAsJsonArray("accessories")?.mapNotNull { el ->
            val a = el.asJsonObjectOrNull() ?: return@mapNotNull null
            val textObj = a.get("text")?.asJsonObjectOrNull()
            val textVal = textObj?.str("value") ?: a.str("text") ?: a.str("date")
            val tagObj = a.get("tag")?.asJsonObjectOrNull()
            RaycastAccessorySpec(
                text = textVal,
                tagText = tagObj?.str("value") ?: a.str("tag"),
                tagColor = parseColorLike(tagObj?.get("color")),
                icon = parseImageSpec(a.get("icon")),
                textColor = parseColorLike(textObj?.get("color")),
            )
        } ?: emptyList()

        return RaycastListItemSpec(
            id = obj.str("id") ?: "${obj.str("title") ?: ""}_$fallbackId",
            title = obj.str("title") ?: "",
            subtitle = obj.str("subtitle"),
            icon = parseImageSpec(obj.get("icon")),
            keywords = obj.strList("keywords"),
            accessories = accessories,
            detailMarkdown = detailMarkdown,
            detailMetadata = detailMeta,
            actions = actions,
        )
    }

    private fun parseGrid(obj: JsonObject): RaycastViewScreen.GridScreen {
        var dropdown: RaycastDropdownSpec? = null
        var actions: RaycastActionPanelSpec? = null
        var emptyView: RaycastEmptyViewSpec? = null
        val sections = mutableListOf<RaycastGridSectionSpec>()
        val looseItems = mutableListOf<RaycastGridItemSpec>()

        for (child in obj.children()) {
            when (child.str("\$t")) {
                "dropdown" -> dropdown = parseDropdown(child)
                "action-panel" -> actions = parseActionPanel(child)
                "empty-view" -> emptyView = parseEmptyView(child)
                "grid-section" -> {
                    val items = child.children().mapIndexedNotNull { idx, c ->
                        if (c.str("\$t") == "grid-item") parseGridItem(c, "gsec_${sections.size}_$idx") else null
                    }
                    sections.add(
                        RaycastGridSectionSpec(
                            title = child.str("title"),
                            subtitle = child.str("subtitle"),
                            columns = child.int("columns"),
                            items = items,
                        ),
                    )
                }
                "grid-item" -> looseItems.add(parseGridItem(child, "gloose_${looseItems.size}"))
            }
        }
        if (looseItems.isNotEmpty()) {
            sections.add(0, RaycastGridSectionSpec(items = looseItems))
        }

        val itemSize = obj.str("itemSize")
        val defaultColumns = when (itemSize?.lowercase()) {
            "small" -> 4
            "large" -> 2
            else -> 3
        }

        return RaycastViewScreen.GridScreen(
            navigationTitle = obj.str("navigationTitle"),
            isLoading = obj.bool("isLoading", false),
            actions = actions,
            columns = obj.int("columns") ?: defaultColumns,
            aspectRatio = obj.str("aspectRatio") ?: "1",
            fit = obj.str("fit") ?: "contain",
            searchText = obj.str("searchText"),
            searchBarPlaceholder = obj.str("searchBarPlaceholder"),
            filtering = obj.bool("filtering", true),
            onSearchTextChangeId = obj.str("onSearchTextChange"),
            dropdown = dropdown,
            emptyView = emptyView,
            sections = sections,
            itemSize = itemSize,
        )
    }

    private fun parseGridItem(obj: JsonObject, fallbackId: String): RaycastGridItemSpec {
        val actions = obj.children().firstOrNull { it.str("\$t") == "action-panel" }?.let { parseActionPanel(it) }
        return RaycastGridItemSpec(
            id = obj.str("id") ?: fallbackId,
            title = obj.str("title"),
            subtitle = obj.str("subtitle"),
            keywords = obj.strList("keywords"),
            content = parseImageSpec(obj.get("content")),
            actions = actions,
        )
    }

    private fun parseDetail(obj: JsonObject): RaycastViewScreen.DetailScreen {
        var actions: RaycastActionPanelSpec? = null
        var metadata: List<RaycastMetadataEntry> = emptyList()
        for (child in obj.children()) {
            when (child.str("\$t")) {
                "action-panel" -> actions = parseActionPanel(child)
                "list-item-detail-metadata" -> metadata = parseMetadata(child)
            }
        }
        return RaycastViewScreen.DetailScreen(
            navigationTitle = obj.str("navigationTitle"),
            isLoading = obj.bool("isLoading", false),
            actions = actions,
            markdown = obj.str("markdown") ?: "",
            metadata = metadata,
        )
    }

    private fun parseForm(obj: JsonObject): RaycastViewScreen.FormScreen {
        var actions: RaycastActionPanelSpec? = null
        val fields = mutableListOf<RaycastFormFieldSpec>()
        for (child in obj.children()) {
            val tag = child.str("\$t") ?: continue
            if (tag == "action-panel") {
                actions = parseActionPanel(child)
                continue
            }
            val kind = tag.removePrefix("form-")
            val hasControlled = child.bool("hasControlledValue", false) ||
                (child.has("value") && !child.get("value").isJsonNull)
            fields.add(
                RaycastFormFieldSpec(
                    kind = kind,
                    id = child.str("id") ?: "field_${fields.size}",
                    title = child.str("title"),
                    placeholder = child.str("placeholder"),
                    info = child.str("info"),
                    error = child.str("error"),
                    label = child.str("label"),
                    textValue = child.str("value") ?: child.str("defaultValue") ?: child.str("text"),
                    boolValue = child.nullableBool("value") ?: child.nullableBool("defaultValue"),
                    onChangeId = child.str("onChange"),
                    options = parseDropdownOptions(child),
                    hasControlledValue = hasControlled,
                    tagValues = child.strList("value").ifEmpty { child.strList("defaultValue") },
                    isFullDay = child.bool("isFullDay", false),
                ),
            )
        }
        return RaycastViewScreen.FormScreen(
            navigationTitle = obj.str("navigationTitle"),
            isLoading = obj.bool("isLoading", false),
            actions = actions,
            fields = fields,
        )
    }

    private fun parseMetadata(obj: JsonObject): List<RaycastMetadataEntry> {
        return obj.children().mapNotNull { child ->
            when (child.str("\$t")) {
                "list-item-detail-metadata-label" -> {
                    val textObj = child.get("text")?.asJsonObjectOrNull()
                    RaycastMetadataEntry(
                        type = "label",
                        title = child.str("title"),
                        text = textObj?.str("value") ?: child.str("text"),
                        icon = parseImageSpec(child.get("icon")),
                        textColor = parseColorLike(child.get("textColor") ?: textObj?.get("color")),
                    )
                }
                "list-item-detail-metadata-link" -> RaycastMetadataEntry(
                    type = "link",
                    title = child.str("title"),
                    text = child.str("text"),
                    target = child.str("target"),
                )
                "list-item-detail-metadata-separator" -> RaycastMetadataEntry(type = "separator")
                "list-item-detail-metadata-tag-list" -> {
                    val tags = child.children().mapNotNull { t ->
                        if (t.str("\$t") == "list-item-detail-metadata-tag-list-item") {
                            (t.str("text") ?: "") to parseColorLike(t.get("color"))
                        } else null
                    }
                    RaycastMetadataEntry(type = "tag-list", title = child.str("title"), tags = tags)
                }
                else -> null
            }
        }
    }

    private fun parseActionPanel(obj: JsonObject): RaycastActionPanelSpec {
        val sections = mutableListOf<RaycastActionSection>()
        val submenus = mutableListOf<RaycastActionSubmenu>()
        val looseActions = mutableListOf<RaycastActionSpec>()
        for (child in obj.children()) {
            when (child.str("\$t")) {
                "action" -> looseActions.add(parseAction(child))
                "action-panel-section" -> {
                    val acts = child.children().mapNotNull { if (it.str("\$t") == "action") parseAction(it) else null }
                    sections.add(RaycastActionSection(title = child.str("title"), actions = acts))
                }
                "action-panel-submenu" -> {
                    val acts = child.children().mapNotNull { if (it.str("\$t") == "action") parseAction(it) else null }
                    submenus.add(
                        RaycastActionSubmenu(
                            title = child.str("title") ?: "More",
                            icon = parseImageSpec(child.get("icon")),
                            onOpenId = child.str("onOpen"),
                            actions = acts,
                        ),
                    )
                }
            }
        }
        if (looseActions.isNotEmpty()) {
            sections.add(0, RaycastActionSection(title = null, actions = looseActions))
        }
        return RaycastActionPanelSpec(title = obj.str("title"), sections = sections, submenus = submenus)
    }

    private fun parseAction(obj: JsonObject): RaycastActionSpec {
        return RaycastActionSpec(
            title = obj.str("title") ?: "Action",
            icon = parseImageSpec(obj.get("icon")),
            style = obj.str("style") ?: "regular",
            onActionId = obj.str("onAction"),
            onSubmitId = obj.str("onSubmit"),
            isSubmit = obj.bool("isSubmit", false),
        )
    }

    private fun parseDropdown(obj: JsonObject): RaycastDropdownSpec {
        return RaycastDropdownSpec(
            tooltip = obj.str("tooltip"),
            placeholder = obj.str("placeholder"),
            value = obj.str("value"),
            defaultValue = obj.str("defaultValue"),
            onChangeId = obj.str("onChange"),
            options = parseDropdownOptions(obj),
        )
    }

    private fun parseDropdownOptions(obj: JsonObject): List<RaycastDropdownOption> {
        val out = mutableListOf<RaycastDropdownOption>()
        for (child in obj.children()) {
            when (child.str("\$t")) {
                "dropdown-item" -> out.add(
                    RaycastDropdownOption(
                        title = child.str("title") ?: child.str("value") ?: "",
                        value = child.str("value") ?: "",
                    ),
                )
                "dropdown-section" -> {
                    val secTitle = child.str("title")
                    for (sub in child.children()) {
                        if (sub.str("\$t") == "dropdown-item") {
                            out.add(
                                RaycastDropdownOption(
                                    title = sub.str("title") ?: sub.str("value") ?: "",
                                    value = sub.str("value") ?: "",
                                    sectionTitle = secTitle,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun parseEmptyView(obj: JsonObject): RaycastEmptyViewSpec {
        val actions = obj.children().firstOrNull { it.str("\$t") == "action-panel" }?.let { parseActionPanel(it) }
        return RaycastEmptyViewSpec(
            title = obj.str("title") ?: "No Results",
            description = obj.str("description"),
            icon = parseImageSpec(obj.get("icon")),
            actions = actions,
        )
    }

    private fun parseColorLike(el: JsonElement?): RaycastColorLike? {
        val obj = el.asJsonObjectOrNull() ?: return null
        val dyn = obj.get("dynamic")?.asJsonObjectOrNull()
        return RaycastColorLike(
            raw = obj.str("raw"),
            light = dyn?.str("light"),
            dark = dyn?.str("dark"),
        )
    }

    private fun parseImageSpec(el: JsonElement?): RaycastImageSpec? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonPrimitive) {
            return RaycastImageSpec(rawSource = el.asString)
        }
        val obj = el.asJsonObjectOrNull() ?: return null
        val colorObj = obj.get("color")?.asJsonObjectOrNull()
        if (colorObj != null) {
            return RaycastImageSpec(colorFill = parseColorLike(colorObj))
        }
        val fileIcon = obj.str("fileIcon") ?: obj.get("fileIcon")?.asJsonObjectOrNull()?.str("path")
        if (!fileIcon.isNullOrBlank()) {
            return RaycastImageSpec(
                fileIcon = fileIcon,
                mask = obj.str("mask"),
                tintColor = parseColorLike(obj.get("tintColor")),
            )
        }
        val sourceObj = obj.get("source")?.asJsonObjectOrNull()
        val themed = sourceObj?.get("themed")?.asJsonObjectOrNull()
        val fallbackObj = obj.get("fallback")?.asJsonObjectOrNull()
        val fallbackRaw = fallbackObj?.str("raw")
            ?: fallbackObj?.get("themed")?.asJsonObjectOrNull()?.str("dark")
            ?: obj.str("fallback")
        return RaycastImageSpec(
            rawSource = sourceObj?.str("raw") ?: obj.str("source"),
            lightSource = themed?.str("light"),
            darkSource = themed?.str("dark"),
            mask = obj.str("mask"),
            tintColor = parseColorLike(obj.get("tintColor")),
            fallbackSource = fallbackRaw,
        )
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
        if (this != null && this.isJsonObject) this.asJsonObject else null

    private fun JsonObject.children(): List<JsonObject> {
        val arr: JsonArray = this.getAsJsonArray("children") ?: return emptyList()
        return arr.mapNotNull { it.asJsonObjectOrNull() }
    }

    private fun JsonObject.str(key: String): String? {
        val el = this.get(key) ?: return null
        return if (el.isJsonPrimitive) el.asString else null
    }

    private fun JsonObject.bool(key: String, default: Boolean): Boolean {
        val el = this.get(key) ?: return default
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) el.asBoolean else default
    }

    private fun JsonObject.nullableBool(key: String): Boolean? {
        val el = this.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) el.asBoolean else null
    }

    private fun JsonObject.int(key: String): Int? {
        val el = this.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) el.asInt else null
    }

    private fun JsonObject.strList(key: String): List<String> {
        val arr = this.getAsJsonArray(key) ?: return emptyList()
        return arr.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
    }
}
