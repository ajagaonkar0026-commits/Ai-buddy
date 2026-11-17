package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.system.VoiceEngineStatus
import com.example.ui.JarvisViewModel
import com.example.ui.components.ArcReactorOrb
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.OperatingModeChip
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ReactorBlue

@Composable
fun CommandHubScreen(
    viewModel: JarvisViewModel,
    onRequestMicPermission: () -> Unit,
    hasMicPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val operatingMode by viewModel.operatingMode.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    var promptInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickCommands = listOf(
        "Prepare my morning briefing",
        "Check my battery and storage",
        "Turn on the flashlight",
        "Create my professional resume",
        "Edit a photo professionally",
        "What is 144 * 24?"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Futuristic HUD Hero Header with Arc Reactor Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1794904565786),
                contentDescription = "${settings.assistantName} Neural Core Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ObsidianBackground.copy(alpha = 0.94f),
                                ObsidianBackground.copy(alpha = 0.72f),
                                ObsidianBackground.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.assistantName.uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = ArcCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${settings.voiceGender.label} • ${settings.aiRoutingMode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OperatingModeChip(mode = operatingMode)
                        if (voiceStatus == VoiceEngineStatus.SPEAKING) {
                            Surface(
                                shape = CircleShape,
                                color = AlertAmber.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AlertAmber)
                            ) {
                                IconButton(
                                    onClick = { viewModel.stopVoiceInput() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.StopCircle, contentDescription = "Interrupt speech", tint = AlertAmber)
                                }
                            }
                        }
                    }
                }

                ArcReactorOrb(
                    assistantName = settings.assistantName,
                    voiceStatus = voiceStatus,
                    isThinking = isThinking,
                    rmsLevel = rmsLevel,
                    onToggleVoice = {
                        when {
                            voiceStatus == VoiceEngineStatus.LISTENING || voiceStatus == VoiceEngineStatus.SPEAKING ->
                                viewModel.stopVoiceInput()
                            !hasMicPermission -> onRequestMicPermission()
                            else -> viewModel.startVoiceInput(activity)
                        }
                    }
                )
            }
        }

        if (partialTranscript.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Heard: \"$partialTranscript\"",
                style = MaterialTheme.typography.labelMedium,
                color = ArcCyan
            )
        }
        if (!voiceError.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = voiceError.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = AlertAmber
            )
        }

        Spacer(Modifier.height(10.dp))
        FirebaseAiNoticeBanner()
        Spacer(Modifier.height(8.dp))

        // Quick Action Prompt Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { cmd ->
                AssistChip(
                    onClick = { viewModel.submitCommand(cmd, activity) },
                    label = { Text(cmd, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = ArcCyan)
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = ElevatedSlate,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, CardBorderSlate)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Conversation Feed Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMMAND STREAM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearChatHistory() },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chat Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Good day, ${settings.userTitle}. ${settings.assistantName} is standing by.",
                                style = MaterialTheme.typography.titleMedium,
                                color = ArcCyan
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Tap the Arc Reactor orb to speak, or type a natural command below. I can control device functions, run multi-step briefings, edit photos and videos, generate PDFs, and query OpenAI or offline local tools.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(message = msg)
                }
            }
        }

        // Command Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                placeholder = { Text("Ask ${settings.assistantName} anything...") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArcCyan,
                    unfocusedBorderColor = CardBorderSlate,
                    focusedContainerColor = MidnightSurface,
                    unfocusedContainerColor = MidnightSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (promptInput.isNotBlank()) {
                            viewModel.submitCommand(promptInput, activity)
                            promptInput = ""
                        }
                    }
                )
            )
            Surface(
                shape = CircleShape,
                color = ArcCyan,
                modifier = Modifier.size(50.dp)
            ) {
                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            viewModel.submitCommand(promptInput, activity)
                            promptInput = ""
                        }
                    },
                    modifier = Modifier.testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = Color(0xFF070B14)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    val containerColor = if (isUser) ReactorBlue.copy(alpha = 0.24f) else MidnightSurface
    val borderColor = when {
        isUser -> ReactorBlue.copy(alpha = 0.6f)
        message.isPlaceholderAi -> AlertAmber.copy(alpha = 0.55f)
        else -> CardBorderSlate
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.providerTag.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) ArcCyan else if (message.isPlaceholderAi) AlertAmber else ArcCyan
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
