package com.sameerasw.essentials.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.launcher.CompatibilityReportSheet
import com.sameerasw.essentials.raycast.AndroidCompatLevel
import com.sameerasw.essentials.raycast.CompatibilityReport
import com.sameerasw.essentials.raycast.InstalledRaycastExtension
import com.sameerasw.essentials.raycast.RaycastAlertRequest
import com.sameerasw.essentials.raycast.RaycastCommandManifest
import com.sameerasw.essentials.raycast.RaycastCompatibilityAnalyzer
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.raycast.RaycastOAuthManager
import com.sameerasw.essentials.raycast.RaycastRuntimeEngine
import com.sameerasw.essentials.raycast.RaycastScreenHost
import com.sameerasw.essentials.ui.theme.EssentialsTheme

class ExtensionRunnerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_EXTENSION_ID = "extension_id"
        const val EXTRA_COMMAND_NAME = "command_name"
        const val EXTRA_ARGUMENTS_JSON = "arguments_json"
        const val EXTRA_LAUNCH_TYPE = "launch_type"

        private const val EXTRA_INTERNAL_TOKEN = "komorebi_internal_launch_token"
        private val internalLaunchToken: String = java.util.UUID.randomUUID().toString()

        fun createIntent(
            context: Context,
            extensionId: String,
            commandName: String,
            arguments: Map<String, String> = emptyMap(),
            launchType: String? = null,
        ): Intent {
            return Intent(context, ExtensionRunnerActivity::class.java).apply {
                putExtra(EXTRA_INTERNAL_TOKEN, internalLaunchToken)
                putExtra(EXTRA_EXTENSION_ID, extensionId)
                putExtra(EXTRA_COMMAND_NAME, commandName)
                if (arguments.isNotEmpty()) {
                    putExtra(EXTRA_ARGUMENTS_JSON, com.google.gson.Gson().toJson(arguments))
                }
                if (!launchType.isNullOrBlank()) {
                    putExtra(EXTRA_LAUNCH_TYPE, launchType)
                }
            }
        }

        private fun parseArguments(raw: String?): Map<String, String> =
            runCatching {
                val json = org.json.JSONObject(raw ?: return emptyMap())
                json.keys().asSequence().associateWith { key -> json.optString(key) }
            }.getOrDefault(emptyMap())
    }

    private var engine: RaycastRuntimeEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (RaycastOAuthManager.handleRedirectUri(intent?.data)) {
            finish()
            return
        }

        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            finish()
            return
        }

        val suppliedToken = intent?.getStringExtra(EXTRA_INTERNAL_TOKEN)
        if (suppliedToken == null || !java.security.MessageDigest.isEqual(
                suppliedToken.toByteArray(Charsets.UTF_8),
                internalLaunchToken.toByteArray(Charsets.UTF_8),
            )
        ) {
            finish()
            return
        }

        val extId = intent.getStringExtra(EXTRA_EXTENSION_ID).orEmpty()
        val cmdName = intent.getStringExtra(EXTRA_COMMAND_NAME).orEmpty()
        val extension = RaycastExtensionRepository.findExtension(this, extId)
        val command = extension?.manifest?.commands?.firstOrNull { it.name == cmdName }
            ?: extension?.manifest?.commands?.firstOrNull()

        if (extension == null || command == null) {
            Toast.makeText(this, "Extension or command not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val runtime = RaycastRuntimeEngine(applicationContext, extension, command)
        engine = runtime
        val launchArguments =
            parseArguments(intent.getStringExtra(EXTRA_ARGUMENTS_JSON))
        val launchType = intent.getStringExtra(EXTRA_LAUNCH_TYPE)

        setContent {
            val isDark = isSystemInDarkTheme()
            EssentialsTheme(darkTheme = isDark) {
                LaunchedEffect(Unit) {
                    runtime.start(
                        arguments = launchArguments,
                        isDarkTheme = isDark,
                        launchType = launchType,
                    )
                }
                ExtensionRunnerScreen(
                    runtime = runtime,
                    extension = extension,
                    command = command,
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        RaycastOAuthManager.handleRedirectUri(intent.data)
    }

    override fun onDestroy() {
        engine?.close()
        engine = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionRunnerScreen(
    runtime: RaycastRuntimeEngine,
    extension: InstalledRaycastExtension,
    command: RaycastCommandManifest,
    onExit: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewStack by runtime.viewStack.collectAsState()
    val errorState by runtime.errorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAlert by remember { mutableStateOf<RaycastAlertRequest?>(null) }
    var showPreferences by remember { mutableStateOf(false) }
    var prefsVersion by remember { mutableIntStateOf(0) }
    var inspectingReport by remember { mutableStateOf<CompatibilityReport?>(null) }

    val baseCompatReport = remember(extension.id, extension.manifest) {
        RaycastCompatibilityAnalyzer.analyzeInstalledExtension(extension)
    }
    val hasPreferences = remember(extension, command) {
        !extension.manifest.preferences.isNullOrEmpty() || !command.preferences.isNullOrEmpty()
    }
    val missingRequiredPrefs = remember(extension.id, command.name, prefsVersion) {
        RaycastExtensionRepository.hasRequiredPreferencesMissing(context, extension, command)
    }

    LaunchedEffect(runtime) {
        runtime.toastEvents.collect { evt ->
            if (!evt.hidden) {
                val text = listOf(evt.title, evt.message).filter { it.isNotBlank() }.joinToString(" — ")
                if (text.isNotBlank()) snackbarHostState.showSnackbar(text)
            }
        }
    }
    LaunchedEffect(runtime) {
        runtime.hudEvents.collect { title ->
            if (title.isNotBlank()) Toast.makeText(context, title, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(runtime) {
        runtime.alertRequests.collect { req -> pendingAlert = req }
    }
    LaunchedEffect(runtime) {
        runtime.closeRequests.collect { onExit() }
    }
    LaunchedEffect(runtime) {
        runtime.openPreferencesRequests.collect { showPreferences = true }
    }

    val handleBack: () -> Unit = {
        if (viewStack.size > 1) {
            runtime.notifyViewPopped()
        } else {
            onExit()
        }
    }
    BackHandler(onBack = handleBack)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact diagnostic & preferences strip above extension host
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val (bg, fg, label, icon) = when (baseCompatReport.level) {
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
                        onClick = { inspectingReport = baseCompatReport },
                        shape = RoundedCornerShape(10.dp),
                        color = bg as androidx.compose.ui.graphics.Color,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (missingRequiredPrefs) {
                            Surface(
                                onClick = { showPreferences = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "Configure Preferences",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        } else if (hasPreferences) {
                            Surface(
                                onClick = { showPreferences = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Settings,
                                        contentDescription = "Preferences",
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "Prefs",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.statusBars),
            ) {
                val activeScreen = viewStack.lastOrNull()
                when {
                    errorState != null -> {
                        val rawError = errorState ?: ""
                        val errorReport = remember(rawError, extension.id) {
                            RaycastCompatibilityAnalyzer.analyzeError(rawError, extension)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Extension Error",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = errorReport.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = rawError,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (errorReport.issues.isNotEmpty()) {
                                Text(
                                    text = "Compatibility Diagnosis",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                errorReport.issues.forEach { issue ->
                                    OutlinedCard(
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = issue.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (issue.severity == AndroidCompatLevel.INCOMPATIBLE) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                            Text(
                                                text = issue.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { inspectingReport = errorReport },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Full Diagnostics")
                                }
                                if (hasPreferences) {
                                    FilledTonalButton(
                                        onClick = { showPreferences = true },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Preferences")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = onExit,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Close")
                            }
                        }
                    }
                    activeScreen != null -> {
                        RaycastScreenHost(
                            screen = activeScreen,
                            extension = extension,
                            commandTitle = command.title.ifBlank { command.name },
                            canNavigateBack = viewStack.size > 1,
                            onBack = handleBack,
                            onInvokeCallback = { id, args -> runtime.invokeCallback(id, *args) },
                            onOpenPreferences = { showPreferences = true },
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("Starting ${command.title.ifBlank { command.name }}…")
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        )
    }

    pendingAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = {
                alert.deferred.complete(false)
                pendingAlert = null
            },
            title = { Text(alert.title) },
            text = if (alert.message.isNotBlank()) {
                { Text(alert.message) }
            } else null,
            confirmButton = {
                TextButton(
                    onClick = {
                        alert.deferred.complete(true)
                        pendingAlert = null
                    },
                ) {
                    Text(
                        text = alert.primaryTitle,
                        color = if (alert.primaryStyle == "Destructive") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        alert.deferred.complete(false)
                        pendingAlert = null
                    },
                ) {
                    Text(alert.dismissTitle)
                }
            },
        )
    }

    if (showPreferences) {
        ExtensionPreferencesBottomSheet(
            extension = extension,
            command = command,
            onDismissRequest = {
                showPreferences = false
                prefsVersion++
            },
        )
    }

    inspectingReport?.let { report ->
        CompatibilityReportSheet(
            title = extension.displayTitle,
            report = report,
            onDismiss = { inspectingReport = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPreferencesBottomSheet(
    extension: InstalledRaycastExtension,
    command: RaycastCommandManifest? = null,
    onDismissRequest: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val defs = remember(extension, command) {
        val raw = if (command != null) {
            extension.manifest.preferences.orEmpty() + command.preferences.orEmpty()
        } else {
            extension.manifest.preferences.orEmpty() +
                extension.manifest.commands.flatMap { it.preferences.orEmpty() }
        }
        raw.distinctBy { it.name }
    }
    val initialValues = remember(extension, command) {
        buildMap<String, Any> {
            putAll(RaycastExtensionRepository.getPreferenceValuesMap(context, extension, command))
            if (command == null) {
                extension.manifest.commands.forEach { cmd ->
                    putAll(RaycastExtensionRepository.getPreferenceValuesMap(context, extension, cmd))
                }
            }
        }
    }
    val editedValues = remember(initialValues) {
        mutableStateMapOf<String, Any>().apply { putAll(initialValues) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${extension.displayTitle} Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (defs.isEmpty()) {
                Text(
                    text = "This extension does not declare configurable preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                defs.forEach { def ->
                    val labelText = buildString {
                        append(def.title.ifBlank { def.name })
                        if (def.required) append(" *")
                    }
                    when (def.type.lowercase()) {
                        "checkbox" -> {
                            val checked = editedValues[def.name] as? Boolean ?: false
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(labelText, style = MaterialTheme.typography.bodyLarge)
                                    if (def.description.isNotBlank()) {
                                        Text(
                                            def.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { editedValues[def.name] = it },
                                )
                            }
                        }
                        "dropdown" -> {
                            val options = def.data.orEmpty()
                            val currentVal = editedValues[def.name]?.toString().orEmpty()
                            val currentTitle = options.firstOrNull { it.value == currentVal }?.title
                                ?: currentVal.ifBlank { "Select option" }
                            var expanded by remember(def.name) { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                            ) {
                                OutlinedTextField(
                                    value = currentTitle,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(labelText) },
                                    supportingText = if (def.description.isNotBlank()) {
                                        { Text(def.description) }
                                    } else null,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    options.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.title.ifBlank { option.value }) },
                                            onClick = {
                                                editedValues[def.name] = option.value
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val value = editedValues[def.name]?.toString() ?: ""
                            OutlinedTextField(
                                value = value,
                                onValueChange = { editedValues[def.name] = it },
                                label = { Text(labelText) },
                                placeholder = if (def.placeholder.isNotBlank()) {
                                    { Text(def.placeholder) }
                                } else null,
                                supportingText = if (def.description.isNotBlank()) {
                                    { Text(def.description) }
                                } else null,
                                visualTransformation = if (def.type.equals("password", ignoreCase = true)) {
                                    PasswordVisualTransformation()
                                } else VisualTransformation.None,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                val permissionState = remember(extension.id) {
                    mutableStateMapOf<com.sameerasw.essentials.raycast.ExtensionCapability, Boolean>().apply {
                        putAll(com.sameerasw.essentials.raycast.ExtensionPermissionManager.getAll(context, extension.id))
                    }
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Runtime Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                com.sameerasw.essentials.raycast.ExtensionCapability.entries.forEach { capability ->
                    val allowed = permissionState[capability] ?: capability.defaultAllowed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = capability.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = capability.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = allowed,
                            onCheckedChange = { permissionState[capability] = it },
                        )
                    }
                }
                Button(
                    onClick = {
                        defs.forEach { def ->
                            editedValues[def.name]?.let { v ->
                                RaycastExtensionRepository.setPreferenceValue(context, extension.id, def, v)
                            }
                        }
                        permissionState.forEach { (capability, allowed) ->
                            com.sameerasw.essentials.raycast.ExtensionPermissionManager.setAllowed(
                                context,
                                extension.id,
                                capability,
                                allowed,
                            )
                        }
                        onDismissRequest()
                        Toast.makeText(context, "Preferences & permissions saved. Reopen command to apply.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Save Preferences")
                }
            }
        }
    }
}
