package com.sameerasw.essentials.assistant.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sameerasw.essentials.assistant.AiMessage
import com.sameerasw.essentials.assistant.AiProviderManager
import com.sameerasw.essentials.assistant.AiStatus
import com.sameerasw.essentials.assistant.AssistantAiMode
import com.sameerasw.essentials.assistant.AssistantAiSettings
import com.sameerasw.essentials.assistant.AssistantApiStyle
import com.sameerasw.essentials.assistant.AssistantProviderPresets
import com.sameerasw.essentials.assistant.OnDeviceAiProvider
import com.sameerasw.essentials.ui.theme.EssentialsTheme
import kotlinx.coroutines.launch

class AssistantSettingsActivity : ComponentActivity() {
    companion object {
        fun intent(context: Context): Intent =
            Intent(context, AssistantSettingsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            EssentialsTheme {
                AssistantSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun AssistantSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AssistantAiSettings.getMode(context)) }
    var onDeviceStatus by remember { mutableStateOf<AiStatus?>(null) }
    var statusLoading by remember { mutableStateOf(true) }
    var onDeviceStatusText by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    var baseUrl by remember { mutableStateOf(AssistantAiSettings.getBaseUrl(context)) }
    var model by remember { mutableStateOf(AssistantAiSettings.getModel(context)) }
    var apiKey by remember { mutableStateOf("") }
    var hasSavedKey by remember {
        mutableStateOf(AssistantAiSettings.readApiKey(context).isNotBlank())
    }
    var testing by remember { mutableStateOf(false) }
    var byokStatusText by remember { mutableStateOf("") }
    var footerMessage by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf(AssistantAiSettings.getProviderId(context)) }
    var apiStyle by remember { mutableStateOf(AssistantAiSettings.getApiStyle(context)) }
    var providerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        statusLoading = true
        onDeviceStatus = runCatching {
            com.sameerasw.essentials.assistant.OnDeviceAiProvider.status(context)
        }.getOrNull()
        onDeviceStatusText = onDeviceStatus?.detail.orEmpty()
        statusLoading = false
    }

    val deviceUnsupported =
        onDeviceStatus?.detail?.contains("not available", ignoreCase = true) == true
    val canDownload =
        mode == AssistantAiMode.ON_DEVICE &&
            onDeviceStatus != null &&
            onDeviceStatus?.available == false &&
            !deviceUnsupported &&
            !downloading

    fun selectMode(selected: AssistantAiMode) {
        mode = selected
        AssistantAiSettings.setMode(context, selected)
        footerMessage = when (selected) {
            AssistantAiMode.OFF -> "AI disabled, deterministic results only"
            AssistantAiMode.ON_DEVICE -> "On-device AI selected"
            AssistantAiMode.BYOK -> "BYOK selected"
        }
    }

    fun saveByok(): Boolean {
        AssistantAiSettings.setProviderId(context, providerId)
        AssistantAiSettings.setApiStyle(context, apiStyle)
        AssistantAiSettings.setBaseUrl(context, baseUrl)
        AssistantAiSettings.setModel(context, model)
        if (apiKey.isNotBlank()) {
            val saved = AssistantAiSettings.saveApiKey(context, apiKey)
            if (!saved) {
                byokStatusText = "Could not securely store API key"
                footerMessage = "Failed to encrypt API key in Android Keystore"
                return false
            }
            apiKey = ""
            hasSavedKey = true
        }
        byokStatusText = "Saved"
        footerMessage = "BYOK settings saved"
        return true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("AI mode")
            SettingsCard {
                ModeOption(
                    title = "Off",
                    description = "Deterministic results only. No AI model is used.",
                    selected = mode == AssistantAiMode.OFF,
                    onSelect = { selectMode(AssistantAiMode.OFF) },
                )
                ModeOption(
                    title = "On-device",
                    description = "Gemini Nano runs on this device. Android only allows it while Komorebi is the top foreground app, so search opens full-screen instead of as an overlay.",
                    selected = mode == AssistantAiMode.ON_DEVICE,
                    onSelect = { selectMode(AssistantAiMode.ON_DEVICE) },
                )
                ModeOption(
                    title = "BYOK",
                    description = "Bring your own key for any OpenAI-compatible endpoint.",
                    selected = mode == AssistantAiMode.BYOK,
                    onSelect = { selectMode(AssistantAiMode.BYOK) },
                )
            }

            SectionHeader("On-device")
            SettingsCard {
                when {
                    statusLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Checking Gemini Nano status…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> {
                        val status = onDeviceStatus
                        Text(
                            text = when {
                                status == null -> "Status unavailable"
                                status.available -> "Available"
                                else -> "Unavailable"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = status?.detail ?: "Could not read Gemini Nano status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = {
                        downloading = true
                        scope.launch {
                            OnDeviceAiProvider.downloadModel(context) { progress ->
                                onDeviceStatusText = progress
                            }
                            downloading = false
                            onDeviceStatus = runCatching {
                                AiProviderManager.status(context)
                            }.getOrNull()
                        }
                    },
                    enabled = canDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading…")
                    } else {
                        Text("Download model")
                    }
                }

                if (onDeviceStatusText.isNotBlank()) {
                    Text(
                        text = onDeviceStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Android blocks AICore inference in overlays, overlays are not treated as foreground, and foreground services do not help. When this mode is active the gesture opens the full-screen search activity so Gemini Nano can run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("BYOK")
            SettingsCard {
                Box {
                    OutlinedButton(
                        onClick = { providerMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            AssistantProviderPresets.byId(providerId)?.label
                                ?: "Custom / ${apiStyle.name.lowercase()}",
                        )
                    }
                    DropdownMenu(
                        expanded = providerMenu,
                        onDismissRequest = { providerMenu = false },
                    ) {
                        AssistantProviderPresets.all.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(preset.label)
                                        Text(
                                            text = preset.style.name.lowercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    providerId = preset.id
                                    apiStyle = preset.style
                                    baseUrl = preset.baseUrl
                                    model = preset.model
                                    AssistantAiSettings.setProviderId(context, preset.id)
                                    AssistantAiSettings.setApiStyle(context, preset.style)
                                    AssistantAiSettings.setBaseUrl(context, preset.baseUrl)
                                    AssistantAiSettings.setModel(context, preset.model)
                                    providerMenu = false
                                    footerMessage = "${preset.label} preset applied"
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "API style: ${apiStyle.name.lowercase()}. Local endpoints such as Ollama and LM Studio can leave the key blank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        Text(
                            if (hasSavedKey) {
                                "A key is saved. Leave blank to keep it."
                            } else if (AssistantAiSettings.isLocalEndpoint(baseUrl)) {
                                "Local endpoint detected; a key is optional."
                            } else {
                                "No key saved yet."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { saveByok() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = {
                            if (!saveByok()) return@OutlinedButton
                            testing = true
                            byokStatusText = ""
                            scope.launch {
                                byokStatusText = runCatching {
                                    val response = com.sameerasw.essentials.assistant.ByokAiProvider.complete(
                                        context = context,
                                        systemPrompt = "You are a test.",
                                        messages = listOf(AiMessage("user", "Reply with OK")),
                                        tools = emptyList(),
                                    )
                                    if (response.error != null) {
                                        "Error: ${response.error}"
                                    } else {
                                        "${response.provider}: ${response.text ?: "empty response"}"
                                    }
                                }.getOrElse { "Error: ${it.message ?: "request failed"}" }
                                testing = false
                            }
                        },
                        enabled = !testing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Test connection")
                        }
                    }
                }
                if (byokStatusText.isNotBlank()) {
                    Text(
                        text = byokStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "The saved API key is never shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            Text(
                text = buildString {
                    append("Mode: ")
                    append(
                        when (mode) {
                            AssistantAiMode.OFF -> "Off"
                            AssistantAiMode.ON_DEVICE -> "On-device"
                            AssistantAiMode.BYOK -> "BYOK"
                        },
                    )
                    if (footerMessage.isNotBlank()) {
                        append(" · ")
                        append(footerMessage)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
