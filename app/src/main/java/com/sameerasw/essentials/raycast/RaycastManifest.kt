package com.sameerasw.essentials.raycast

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.File

data class RaycastPreferenceOption(
    val title: String = "",
    val value: String = "",
)

data class RaycastPreferenceDef(
    val name: String = "",
    val type: String = "textfield",
    val title: String = "",
    val description: String = "",
    val required: Boolean = false,
    val placeholder: String = "",
    @SerializedName("default")
    val defaultValue: JsonElement? = null,
    val data: List<RaycastPreferenceOption>? = null,
)

data class RaycastArgumentDef(
    val name: String = "",
    val type: String = "text",
    val placeholder: String = "",
    val required: Boolean = false,
)

data class RaycastCommandManifest(
    val name: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val description: String = "",
    val mode: String = "view",
    val icon: String? = null,
    val keywords: List<String>? = null,
    val preferences: List<RaycastPreferenceDef>? = null,
    val arguments: List<RaycastArgumentDef>? = null,
    val disabledByDefault: Boolean? = null,
)

data class RaycastPackageManifest(
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String? = null,
    val author: String = "",
    val owner: String? = null,
    val version: String? = null,
    val categories: List<String>? = null,
    val commands: List<RaycastCommandManifest> = emptyList(),
    val preferences: List<RaycastPreferenceDef>? = null,
)

data class InstalledRaycastExtension(
    val id: String,
    val dirPath: String,
    val manifest: RaycastPackageManifest,
    val isBuiltInStarter: Boolean = false,
) {
    val displayTitle: String
        get() = manifest.title.ifBlank { manifest.name }

    val authorName: String
        get() = manifest.owner?.takeIf { it.isNotBlank() } ?: manifest.author.ifBlank { "Community" }

    fun entryFileForCommand(commandName: String): File? {
        val dir = File(dirPath)
        val candidates = listOf(
            File(dir, "$commandName.js"),
            File(dir, "dist/$commandName.js"),
            File(dir, "src/$commandName.js"),
            File(dir, "$commandName/index.js"),
            File(dir, "index.js"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }
}

object RaycastExtensionRepository {
    private val gson = Gson()
    private const val PREF_FILE = "raycast_extension_preferences"
    private const val STORAGE_PREFIX = "raycast_ext_storage_"
    private const val CACHE_PREFIX = "raycast_ext_cache_"

    fun extensionsRootDir(context: Context): File {
        val dir = File(context.filesDir, "raycast_extensions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun supportDir(context: Context, extensionId: String): File {
        val dir = File(context.filesDir, "raycast_support/$extensionId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun ensureStarterExtensions(context: Context) {
        val root = extensionsRootDir(context)
        val marker = File(root, ".starters_v1")
        if (marker.exists()) return

        installStarterNotes(File(root, "essentials-quick-notes"))
        installStarterColorPalette(File(root, "essentials-color-studio"))
        installStarterJsonUuid(File(root, "essentials-dev-toolkit"))
        runCatching { marker.writeText("1") }
    }

    fun listInstalledExtensions(context: Context): List<InstalledRaycastExtension> {
        ensureStarterExtensions(context)
        val root = extensionsRootDir(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val pkgFile = File(dir, "package.json")
            if (!pkgFile.exists()) return@mapNotNull null
            runCatching {
                val manifest = gson.fromJson(pkgFile.readText(), RaycastPackageManifest::class.java)
                val isStarter = File(dir, ".builtin_starter").exists()
                InstalledRaycastExtension(
                    id = dir.name,
                    dirPath = dir.absolutePath,
                    manifest = manifest,
                    isBuiltInStarter = isStarter,
                )
            }.getOrNull()
        }.sortedBy { it.displayTitle.lowercase() }
    }

    fun findExtension(context: Context, extensionId: String): InstalledRaycastExtension? {
        return listInstalledExtensions(context).firstOrNull { it.id == extensionId }
    }

    fun uninstallExtension(context: Context, extensionId: String): Boolean {
        val dir = File(extensionsRootDir(context), extensionId)
        if (!dir.exists()) return false
        context.getSharedPreferences(STORAGE_PREFIX + extensionId, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(CACHE_PREFIX + extensionId, Context.MODE_PRIVATE).edit().clear().apply()
        return dir.deleteRecursively()
    }

    fun getPreferenceValuesMap(
        context: Context,
        extension: InstalledRaycastExtension,
        command: RaycastCommandManifest?,
    ): Map<String, Any> {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val result = linkedMapOf<String, Any>()
        val allDefs = (extension.manifest.preferences.orEmpty()) + (command?.preferences.orEmpty())
        for (def in allDefs) {
            val key = "${extension.id}::${def.name}"
            if (prefs.contains(key)) {
                when (def.type.lowercase()) {
                    "checkbox" -> result[def.name] = prefs.getBoolean(key, false)
                    else -> result[def.name] = prefs.getString(key, "") ?: ""
                }
            } else if (def.defaultValue != null && !def.defaultValue.isJsonNull) {
                if (def.type.lowercase() == "checkbox" && def.defaultValue.isJsonPrimitive) {
                    result[def.name] = def.defaultValue.asBoolean
                } else {
                    result[def.name] = def.defaultValue.asString
                }
            }
        }
        return result
    }

    fun setPreferenceValue(context: Context, extensionId: String, def: RaycastPreferenceDef, value: Any) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
        val key = "$extensionId::${def.name}"
        when (def.type.lowercase()) {
            "checkbox" -> prefs.putBoolean(key, value as? Boolean ?: (value.toString() == "true"))
            else -> prefs.putString(key, value.toString())
        }
        prefs.apply()
    }

    fun localStoragePrefs(context: Context, extensionId: String) =
        context.getSharedPreferences(STORAGE_PREFIX + extensionId, Context.MODE_PRIVATE)

    fun cachePrefs(context: Context, extensionId: String) =
        context.getSharedPreferences(CACHE_PREFIX + extensionId, Context.MODE_PRIVATE)

    private fun installStarterNotes(dir: File) {
        if (dir.exists()) return
        dir.mkdirs()
        File(dir, ".builtin_starter").writeText("1")
        File(dir, "package.json").writeText(
            """
            {
              "name": "essentials-quick-notes",
              "title": "Quick Notes",
              "description": "Capture and search markdown notes stored locally using @raycast/api LocalStorage.",
              "author": "Essentials",
              "version": "1.0.0",
              "categories": ["Productivity"],
              "preferences": [
                {
                  "name": "defaultTag",
                  "type": "textfield",
                  "title": "Default Note Tag",
                  "description": "Default tag applied to newly created notes",
                  "default": "note"
                }
              ],
              "commands": [
                {
                  "name": "browse-notes",
                  "title": "Quick Notes",
                  "subtitle": "Raycast Runtime",
                  "description": "Browse, search, and create notes",
                  "mode": "view"
                },
                {
                  "name": "create-note",
                  "title": "New Note",
                  "subtitle": "Quick Notes",
                  "description": "Create a new note via Form",
                  "mode": "view"
                }
              ]
            }
            """.trimIndent(),
        )
        File(dir, "browse-notes.js").writeText(
            """
            const React = require("react");
            const { List, Detail, Form, ActionPanel, Action, Icon, Color, LocalStorage, showToast, Toast, useNavigation, getPreferenceValues } = require("@raycast/api");

            function NoteEditor({ onSave }) {
              const { pop } = useNavigation();
              const prefs = getPreferenceValues();
              const [title, setTitle] = React.useState("");
              const [body, setBody] = React.useState("");
              const [tag, setTag] = React.useState(prefs.defaultTag || "note");
              return React.createElement(Form, {
                navigationTitle: "Create Note",
                actions: React.createElement(ActionPanel, null,
                  React.createElement(Action.SubmitForm, {
                    title: "Save Note",
                    icon: Icon.Check,
                    onSubmit: async () => {
                      if (!title.trim()) {
                        await showToast({ style: Toast.Style.Failure, title: "Title is required" });
                        return;
                      }
                      await onSave({ id: String(Date.now()), title: title.trim(), body, tag: tag.trim() || "note", updatedAt: new Date().toISOString() });
                      await showToast({ style: Toast.Style.Success, title: "Note saved" });
                      pop();
                    }
                  })
                )
              },
                React.createElement(Form.TextField, { id: "title", title: "Title", placeholder: "Note title...", value: title, onChange: setTitle }),
                React.createElement(Form.TextField, { id: "tag", title: "Tag", placeholder: "work, idea, todo", value: tag, onChange: setTag }),
                React.createElement(Form.TextArea, { id: "body", title: "Content (Markdown)", placeholder: "Write your note...", value: body, onChange: setBody })
              );
            }

            function NoteDetail({ note, onDelete }) {
              const { pop } = useNavigation();
              return React.createElement(Detail, {
                navigationTitle: note.title,
                markdown: "# " + note.title + "\n\n" + (note.body || "_Empty note_"),
                metadata: React.createElement(Detail.Metadata, null,
                  React.createElement(Detail.Metadata.Label, { title: "Tag", text: note.tag || "note" }),
                  React.createElement(Detail.Metadata.Separator),
                  React.createElement(Detail.Metadata.Label, { title: "Updated", text: (note.updatedAt || "").slice(0, 10) })
                ),
                actions: React.createElement(ActionPanel, null,
                  React.createElement(Action.CopyToClipboard, { title: "Copy Markdown", content: "# " + note.title + "\n\n" + (note.body || "") }),
                  React.createElement(Action, { title: "Delete Note", icon: Icon.Trash, style: Action.Style.Destructive, onAction: async () => { await onDelete(note.id); pop(); } })
                )
              });
            }

            module.exports = function BrowseNotes() {
              const [notes, setNotes] = React.useState([]);
              const [loading, setLoading] = React.useState(true);
              const [search, setSearch] = React.useState("");
              const [tagFilter, setTagFilter] = React.useState("all");

              React.useEffect(() => {
                LocalStorage.getItem("notes_v1").then((raw) => {
                  if (raw) {
                    try { setNotes(JSON.parse(raw)); } catch (_) {}
                  } else {
                    const seed = [
                      { id: "welcome", title: "Welcome to Raycast Extensions on Android", tag: "guide", body: "This extension runs inside QuickJS with React, LocalStorage, Form, Detail, and Material 3 Expressive components.", updatedAt: "2026-01-01" }
                    ];
                    setNotes(seed);
                    LocalStorage.setItem("notes_v1", JSON.stringify(seed));
                  }
                  setLoading(false);
                });
              }, []);

              const persist = async (next) => {
                setNotes(next);
                await LocalStorage.setItem("notes_v1", JSON.stringify(next));
              };

              const addNote = async (n) => { await persist([n].concat(notes)); };
              const deleteNote = async (id) => { await persist(notes.filter((x) => x.id !== id)); };

              const tags = Array.from(new Set(notes.map((n) => n.tag || "note")));
              const filtered = notes.filter((n) => {
                if (tagFilter !== "all" && (n.tag || "note") !== tagFilter) return false;
                if (!search.trim()) return true;
                const q = search.toLowerCase();
                return (n.title || "").toLowerCase().includes(q) || (n.body || "").toLowerCase().includes(q);
              });

              return React.createElement(List, {
                isLoading: loading,
                searchText: search,
                onSearchTextChange: setSearch,
                searchBarPlaceholder: "Search notes...",
                searchBarAccessory: React.createElement(List.Dropdown, { tooltip: "Filter by tag", value: tagFilter, onChange: setTagFilter },
                  React.createElement(List.Dropdown.Item, { title: "All Tags", value: "all" }),
                  tags.map((t) => React.createElement(List.Dropdown.Item, { key: t, title: t, value: t }))
                ),
                actions: React.createElement(ActionPanel, null,
                  React.createElement(Action.Push, { title: "New Note", icon: Icon.Plus, target: React.createElement(NoteEditor, { onSave: addNote }) })
                )
              },
                filtered.length === 0
                  ? React.createElement(List.EmptyView, { title: "No matching notes", description: "Tap New Note below to create one", icon: Icon.Document })
                  : filtered.map((note) => React.createElement(List.Item, {
                      key: note.id,
                      id: note.id,
                      title: note.title,
                      subtitle: (note.body || "").replace(/\n/g, " ").slice(0, 60),
                      icon: Icon.Document,
                      accessories: [{ tag: { value: note.tag || "note", color: Color.Blue } }],
                      actions: React.createElement(ActionPanel, null,
                        React.createElement(Action.Push, { title: "View Note", icon: Icon.Eye, target: React.createElement(NoteDetail, { note, onDelete: deleteNote }) }),
                        React.createElement(Action.Push, { title: "New Note", icon: Icon.Plus, target: React.createElement(NoteEditor, { onSave: addNote }) }),
                        React.createElement(Action.CopyToClipboard, { title: "Copy Content", content: note.body || note.title }),
                        React.createElement(Action, { title: "Delete", icon: Icon.Trash, style: Action.Style.Destructive, onAction: () => deleteNote(note.id) })
                      )
                    }))
              );
            };
            """.trimIndent(),
        )
        File(dir, "create-note.js").writeText(
            """
            const React = require("react");
            const { Form, ActionPanel, Action, Icon, LocalStorage, showToast, Toast, popToRoot } = require("@raycast/api");
            module.exports = function CreateNote() {
              const [title, setTitle] = React.useState("");
              const [body, setBody] = React.useState("");
              return React.createElement(Form, {
                navigationTitle: "New Quick Note",
                actions: React.createElement(ActionPanel, null,
                  React.createElement(Action.SubmitForm, {
                    title: "Save Note",
                    icon: Icon.Check,
                    onSubmit: async () => {
                      const raw = await LocalStorage.getItem("notes_v1");
                      const list = raw ? JSON.parse(raw) : [];
                      list.unshift({ id: String(Date.now()), title: title || "Untitled", body, tag: "note", updatedAt: new Date().toISOString() });
                      await LocalStorage.setItem("notes_v1", JSON.stringify(list));
                      await showToast({ style: Toast.Style.Success, title: "Saved Note" });
                      await popToRoot();
                    }
                  })
                )
              },
                React.createElement(Form.TextField, { id: "title", title: "Title", value: title, onChange: setTitle }),
                React.createElement(Form.TextArea, { id: "body", title: "Body", value: body, onChange: setBody })
              );
            };
            """.trimIndent(),
        )
    }

    private fun installStarterColorPalette(dir: File) {
        if (dir.exists()) return
        dir.mkdirs()
        File(dir, ".builtin_starter").writeText("1")
        File(dir, "package.json").writeText(
            """
            {
              "name": "essentials-color-studio",
              "title": "Color Studio",
              "description": "Browse curated Material & Tailwind color palettes in a Raycast Grid component.",
              "author": "Essentials",
              "version": "1.0.0",
              "categories": ["Design"],
              "commands": [
                {
                  "name": "palette",
                  "title": "Browse Colors",
                  "subtitle": "Color Studio",
                  "description": "Copy HEX or RGB color tokens",
                  "mode": "view"
                }
              ]
            }
            """.trimIndent(),
        )
        File(dir, "palette.js").writeText(
            """
            const React = require("react");
            const { Grid, ActionPanel, Action, Icon } = require("@raycast/api");
            const COLORS = [
              { name: "Indigo 500", hex: "#6366F1" },
              { name: "Sky 500", hex: "#0EA5E9" },
              { name: "Emerald 500", hex: "#10B981" },
              { name: "Amber 500", hex: "#F59E0B" },
              { name: "Rose 500", hex: "#F43F5E" },
              { name: "Violet 500", hex: "#8B5CF6" },
              { name: "Teal 500", hex: "#14B8A6" },
              { name: "Orange 500", hex: "#F97316" },
              { name: "Slate 900", hex: "#0F172A" }
            ];
            module.exports = function ColorPalette() {
              const [q, setQ] = React.useState("");
              const items = COLORS.filter((c) => !q || c.name.toLowerCase().includes(q.toLowerCase()) || c.hex.toLowerCase().includes(q.toLowerCase()));
              return React.createElement(Grid, {
                columns: 3,
                searchText: q,
                onSearchTextChange: setQ,
                searchBarPlaceholder: "Filter colors..."
              },
                items.map((c) => React.createElement(Grid.Item, {
                  key: c.hex,
                  id: c.hex,
                  title: c.name,
                  subtitle: c.hex,
                  content: { color: c.hex },
                  actions: React.createElement(ActionPanel, null,
                    React.createElement(Action.CopyToClipboard, { title: "Copy HEX", content: c.hex, icon: Icon.Clipboard })
                  )
                }))
              );
            };
            """.trimIndent(),
        )
    }

    private fun installStarterJsonUuid(dir: File) {
        if (dir.exists()) return
        dir.mkdirs()
        File(dir, ".builtin_starter").writeText("1")
        File(dir, "package.json").writeText(
            """
            {
              "name": "essentials-dev-toolkit",
              "title": "Dev Toolkit",
              "description": "Generate UUIDs, hashes, and inspect Base64/JSON strings locally.",
              "author": "Essentials",
              "version": "1.0.0",
              "categories": ["Developer Tools"],
              "commands": [
                {
                  "name": "toolkit",
                  "title": "UUID & Hash Generator",
                  "subtitle": "Dev Toolkit",
                  "description": "Generate UUID v4 and SHA-256 digests",
                  "mode": "view"
                }
              ]
            }
            """.trimIndent(),
        )
        File(dir, "toolkit.js").writeText(
            """
            const React = require("react");
            const crypto = require("node:crypto");
            const { List, ActionPanel, Action, Icon, Color, Detail } = require("@raycast/api");
            module.exports = function DevToolkit() {
              const [input, setInput] = React.useState("komorebi");
              const [uuids, setUuids] = React.useState(() => [crypto.randomUUID(), crypto.randomUUID(), crypto.randomUUID()]);
              const sha256 = crypto.createHash("sha256").update(input).digest("hex");
              const b64 = Buffer.from(input).toString("base64");

              return React.createElement(List, {
                searchText: input,
                onSearchTextChange: setInput,
                searchBarPlaceholder: "Type text to hash or Base64 encode..."
              },
                React.createElement(List.Section, { title: "Hashes & Encoding" },
                  React.createElement(List.Item, {
                    title: "SHA-256",
                    subtitle: sha256,
                    icon: Icon.Lock,
                    accessories: [{ tag: { value: "64 hex", color: Color.Green } }],
                    actions: React.createElement(ActionPanel, null,
                      React.createElement(Action.CopyToClipboard, { title: "Copy SHA-256", content: sha256 })
                    )
                  }),
                  React.createElement(List.Item, {
                    title: "Base64",
                    subtitle: b64,
                    icon: Icon.Code,
                    actions: React.createElement(ActionPanel, null,
                      React.createElement(Action.CopyToClipboard, { title: "Copy Base64", content: b64 })
                    )
                  })
                ),
                React.createElement(List.Section, { title: "Generated UUIDs" },
                  uuids.map((id, i) => React.createElement(List.Item, {
                    key: id + i,
                    title: id,
                    icon: Icon.Key,
                    actions: React.createElement(ActionPanel, null,
                      React.createElement(Action.CopyToClipboard, { title: "Copy UUID", content: id }),
                      React.createElement(Action, { title: "Regenerate UUIDs", icon: Icon.ArrowClockwise, onAction: () => setUuids([crypto.randomUUID(), crypto.randomUUID(), crypto.randomUUID()]) })
                    )
                  }))
                )
              );
            };
            """.trimIndent(),
        )
    }
}
