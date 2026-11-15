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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.raycast.InstalledRaycastExtension
import com.sameerasw.essentials.raycast.RaycastAlertRequest
import com.sameerasw.essentials.raycast.RaycastCommandManifest
import com.sameerasw.essentials.raycast.RaycastExtensionRepository
import com.sameerasw.essentials.raycast.RaycastOAuthManager
import com.sameerasw.essentials.raycast.RaycastRuntimeEngine
import com.sameerasw.essentials.raycast.RaycastScreenHost
import com.sameerasw.essentials.ui.theme.EssentialsTheme

class ExtensionRunnerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_EXTENSION_ID = "extension_id"
        const val EXTRA_COMMAND_NAME = "command_name"

        fun createIntent(context: Context, extensionId: String, commandName: String): Intent {
            return Intent(context, ExtensionRunnerActivity::class.java).apply {
                putExtra(EXTRA_EXTENSION_ID, extensionId)
                putExtra(EXTRA_COMMAND_NAME, commandName)
            }
        }
    }

    private var engine: RaycastRuntimeEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (RaycastOAuthManager.handleRedirectUri(intent?.data)) {
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

        setContent {
            val isDark = isSystemInDarkTheme()
            EssentialsTheme(darkTheme = isDark) {
                LaunchedEffect(Unit) {
                    runtime.start(isDarkTheme = isDark)
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
        val activeScreen = viewStack.lastOrNull()
        when {
            errorState != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Extension Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorState ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onExit) { Text("Close") }
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
            onDismissRequest = { showPreferences = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPreferencesBottomSheet(
    extension: InstalledRaycastExtension,
    command: RaycastCommandManifest,
    onDismissRequest: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val defs = remember(extension, command) {
        (extension.manifest.preferences.orEmpty()) + (command.preferences.orEmpty())
    }
    val initialValues = remember(extension, command) {
        RaycastExtensionRepository.getPreferenceValuesMap(context, extension, command)
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
                    when (def.type.lowercase()) {
                        "checkbox" -> {
                            val checked = editedValues[def.name] as? Boolean ?: false
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(def.title.ifBlank { def.name }, style = MaterialTheme.typography.bodyLarge)
                                    if (def.description.isNotBlank()) {
                                        Text(def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { editedValues[def.name] = it },
                                )
                            }
                        }
                        else -> {
                            val value = editedValues[def.name]?.toString() ?: ""
                            OutlinedTextField(
                                value = value,
                                onValueChange = { editedValues[def.name] = it },
                                label = { Text(def.title.ifBlank { def.name }) },
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
                Button(
                    onClick = {
                        defs.forEach { def ->
                            editedValues[def.name]?.let { v ->
                                RaycastExtensionRepository.setPreferenceValue(context, extension.id, def, v)
                            }
                        }
                        onDismissRequest()
                        Toast.makeText(context, "Preferences saved. Reopen command to apply.", Toast.LENGTH_SHORT).show()
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
