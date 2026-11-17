package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AiRoutingMode
import com.example.data.preferences.GreetingStyle
import com.example.data.preferences.VoiceGender
import com.example.ui.JarvisViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnlineEmerald
import java.util.Locale

data class PermissionStatusItem(
    val name: String,
    val granted: Boolean,
    val onRequest: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel,
    permissions: List<PermissionStatusItem>,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()

    var nameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var titleInput by remember(settings.userTitle) { mutableStateOf(settings.userTitle) }
    var greetingStyle by remember(settings.greetingStyle) { mutableStateOf(settings.greetingStyle) }
    var wakeWordEnabled by remember(settings.wakeWordEnabled) { mutableStateOf(settings.wakeWordEnabled) }

    var voiceGender by remember(settings.voiceGender) { mutableStateOf(settings.voiceGender) }
    var selectedVoiceName by remember(settings.selectedVoiceName) { mutableStateOf(settings.selectedVoiceName) }
    var languageTag by remember(settings.voiceLanguageTag) { mutableStateOf(settings.voiceLanguageTag) }
    var speechRate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var speechPitch by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }
    var continuousListening by remember(settings.continuousListening) { mutableStateOf(settings.continuousListening) }
    var autoSpeak by remember(settings.autoSpeakReplies) { mutableStateOf(settings.autoSpeakReplies) }

    var profileName by remember(settings.profileFullName) { mutableStateOf(settings.profileFullName) }
    var profileRole by remember(settings.profileRole) { mutableStateOf(settings.profileRole) }
    var profileEmail by remember(settings.profileEmail) { mutableStateOf(settings.profileEmail) }
    var profileSkills by remember(settings.profileSkills) { mutableStateOf(settings.profileSkills) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("IDENTITY, VOICE & SETTINGS", style = MaterialTheme.typography.headlineMedium, color = ArcCyan)

        // 1. Customizable Assistant Name & Identity
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = ArcCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("CUSTOMIZABLE AI IDENTITY", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JARVIS", "FRIDAY", "NOVA", "ATLAS", "PERSONAL BUDDY").forEach { preset ->
                        FilterChip(
                            selected = nameInput.equals(preset, ignoreCase = true),
                            onClick = { nameInput = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Assistant Name") },
                        modifier = Modifier.weight(1f).testTag("assistant_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Address User As") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                }

                Text("Greeting Style", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreetingStyle.entries.forEach { style ->
                        FilterChip(
                            selected = greetingStyle == style,
                            onClick = { greetingStyle = style },
                            label = { Text(style.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Wake-Word Recognition (\"$nameInput\")", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = wakeWordEnabled, onCheckedChange = { wakeWordEnabled = it })
                }

                Button(
                    onClick = {
                        viewModel.updateIdentity(nameInput, titleInput, greetingStyle, wakeWordEnabled)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground),
                    modifier = Modifier.fillMaxWidth().testTag("save_identity_button")
                ) {
                    Text("Save Assistant Name & Identity")
                }
            }
        }

        // 2. Male & Female AI Voice System
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = ArcCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("MALE & FEMALE VOICE ENGINE", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceGender.entries.forEach { gender ->
                        FilterChip(
                            selected = voiceGender == gender,
                            onClick = {
                                voiceGender = gender
                                selectedVoiceName = ""
                            },
                            label = { Text(gender.label) }
                        )
                    }
                }

                Text("Language / Accent", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en-US", "en-GB", "en-IN", "en-AU", "es-ES", "fr-FR", "de-DE").forEach { tag ->
                        FilterChip(
                            selected = languageTag == tag,
                            onClick = { languageTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                val filteredVoices = availableVoices.filter {
                    (voiceGender == VoiceGender.NEUTRAL || it.inferredGender == voiceGender) &&
                        it.languageTag.startsWith(languageTag.substringBefore("-"), ignoreCase = true)
                }.take(6)

                if (filteredVoices.isNotEmpty()) {
                    Text("Available Device Voice Profiles", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredVoices.forEach { vp ->
                            FilterChip(
                                selected = selectedVoiceName == vp.id,
                                onClick = { selectedVoiceName = vp.id },
                                label = { Text(vp.displayName) }
                            )
                        }
                    }
                }

                Text("Speech Speed: ${String.format(Locale.US, "%.2fx", speechRate)}", style = MaterialTheme.typography.bodyMedium)
                Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.6f..1.8f)

                Text("Voice Pitch: ${String.format(Locale.US, "%.2fx", speechPitch)}", style = MaterialTheme.typography.bodyMedium)
                Slider(value = speechPitch, onValueChange = { speechPitch = it }, valueRange = 0.6f..1.6f)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Speak Assistant Replies", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoSpeak, onCheckedChange = { autoSpeak = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Continuous Conversation Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = continuousListening, onCheckedChange = { continuousListening = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.previewVoice(
                                settings.copy(
                                    assistantName = nameInput.ifBlank { "JARVIS" },
                                    userTitle = titleInput.ifBlank { "Sir" },
                                    voiceGender = voiceGender,
                                    selectedVoiceName = selectedVoiceName,
                                    voiceLanguageTag = languageTag,
                                    speechRate = speechRate,
                                    speechPitch = speechPitch
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("preview_voice_button")
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview Voice")
                    }
                    Button(
                        onClick = {
                            viewModel.updateVoiceSettings(
                                voiceGender, selectedVoiceName, languageTag, speechRate, speechPitch, continuousListening, autoSpeak
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground),
                        modifier = Modifier.weight(1f).testTag("save_voice_button")
                    ) {
                        Text("Save Voice")
                    }
                }
            }
        }

        // 3. AI Orchestration & OpenAI Knowledge Engine
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI BRAIN & OPENAI ENGINE", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                Text(
                    text = if (viewModel.openAiRepository.isConfigured) {
                        "OpenAI Responses API: Configured via Secrets."
                    } else {
                        "OpenAI Responses API: Add OPENAI_API_KEY in AI Studio Secrets to enable live OpenAI knowledge & web research."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (viewModel.openAiRepository.isConfigured) OnlineEmerald else AlertAmber
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiRoutingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.aiRoutingMode == mode,
                            onClick = { viewModel.updateAiSettings(settings.openAiModel, settings.openAiWebSearch, mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text("OpenAI Responses Model", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("gpt-4.1-mini", "gpt-4o", "o4-mini").forEach { model ->
                        FilterChip(
                            selected = settings.openAiModel == model,
                            onClick = { viewModel.updateAiSettings(model, settings.openAiWebSearch, settings.aiRoutingMode) },
                            label = { Text(model) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("OpenAI Web Research Tool", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.openAiWebSearch,
                        onCheckedChange = { enabled ->
                            viewModel.updateAiSettings(settings.openAiModel, enabled, settings.aiRoutingMode)
                        }
                    )
                }
            }
        }

        // 4. Security Gates & Permissions Center
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = ArcCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("SECURITY & PERMISSIONS", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Confirm Before Making Calls", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.confirmCalls, onCheckedChange = {
                        viewModel.updateSecuritySettings(it, settings.confirmMessages, settings.confirmDeletions)
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Confirm Before Drafting Messages", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.confirmMessages, onCheckedChange = {
                        viewModel.updateSecuritySettings(settings.confirmCalls, it, settings.confirmDeletions)
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Status Details in Notification", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.showNotificationStatusDetails, onCheckedChange = {
                        viewModel.updateNotificationDetails(it)
                    })
                }

                Text("Android Runtime Permissions", style = MaterialTheme.typography.titleMedium)
                permissions.forEach { perm ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(perm.name, style = MaterialTheme.typography.bodyMedium)
                        if (perm.granted) {
                            Text("Granted", style = MaterialTheme.typography.labelMedium, color = OnlineEmerald)
                        } else {
                            FilledTonalButton(onClick = perm.onRequest) {
                                Text("Grant")
                            }
                        }
                    }
                }
            }
        }

        // 5. Personal Profile for Resume & Briefing Personalization
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PERSONAL PROFILE (LOCAL)", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = profileRole, onValueChange = { profileRole = it }, label = { Text("Profession / Role") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = profileEmail, onValueChange = { profileEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = profileSkills, onValueChange = { profileSkills = it }, label = { Text("Core Skills") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.updateUserProfile(profileName, profileRole, profileEmail, profileSkills) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Personal Profile")
                }
            }
        }
    }
}
