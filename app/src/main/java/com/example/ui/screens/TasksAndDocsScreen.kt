package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.documents.ResumeInput
import com.example.ui.JarvisViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnlineEmerald

@Composable
fun TasksAndDocsScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Tasks & Notes, 1 = Docs & Resume, 2 = Automations
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val logs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val docsState by viewModel.docsState.collectAsStateWithLifecycle()

    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var reminderInput by remember { mutableStateOf("") }

    var fullName by remember(settings.profileFullName) { mutableStateOf(settings.profileFullName) }
    var roleTitle by remember(settings.profileRole) { mutableStateOf(settings.profileRole) }
    var email by remember(settings.profileEmail) { mutableStateOf(settings.profileEmail) }
    var phone by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("Dedicated professional experienced in delivering high-impact projects and cross-functional leadership.") }
    var skills by remember(settings.profileSkills) { mutableStateOf(settings.profileSkills.ifBlank { "Kotlin, Android, AI Systems, Architecture" }) }
    var experience by remember { mutableStateOf("Senior Engineer — Built scalable mobile systems and intelligent automation workflows.") }
    var education by remember { mutableStateOf("B.S. in Computer Science") }

    var reportTitle by remember { mutableStateOf("Research & Intelligence Briefing") }
    var reportBody by remember { mutableStateOf("") }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectPickedDocument(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("ASSISTANT & DOCUMENTS", style = MaterialTheme.typography.headlineMedium, color = ArcCyan)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 3)
            ) { Text("Tasks") }
            SegmentedButton(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 3)
            ) { Text("Docs/PDF") }
            SegmentedButton(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(2, 3)
            ) { Text("History") }
        }

        when (activeTab) {
            0 -> {
                // Morning Briefing Trigger
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Morning Briefing", style = MaterialTheme.typography.titleMedium, color = ArcCyan)
                            Text("Checks calendar, weather, device status & open reminders, then reads aloud.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.submitCommand("Prepare my morning briefing") },
                            colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Run")
                        }
                    }
                }

                // Reminders / To-Do
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REMINDERS & TO-DO (${reminders.count { !it.isCompleted }} OPEN)", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = reminderInput,
                                onValueChange = { reminderInput = it },
                                label = { Text("New task or reminder") },
                                modifier = Modifier.weight(1f).testTag("new_reminder_input"),
                                singleLine = true
                            )
                            FilledTonalButton(
                                onClick = {
                                    viewModel.addReminder(reminderInput, "Today", "Normal")
                                    reminderInput = ""
                                },
                                modifier = Modifier.testTag("add_reminder_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add reminder")
                            }
                        }
                        if (reminders.isEmpty()) {
                            Text("No reminders yet. Add one above or ask ${settings.assistantName}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            reminders.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.toggleReminder(item.id, !item.isCompleted) }) {
                                        Icon(
                                            imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = "Toggle complete",
                                            tint = if (item.isCompleted) OnlineEmerald else ArcCyan
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                        Text("${item.dueTimeText} • ${item.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.deleteReminder(item.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Local Notes
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOCAL NOTES (OFFLINE-READY)", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("Note title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = noteBody,
                            onValueChange = { noteBody = it },
                            label = { Text("Write a note...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.saveNote(noteTitle, noteBody)
                                noteTitle = ""
                                noteBody = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_note_button")
                        ) {
                            Text("Save Note Locally")
                        }
                        notes.forEach { note ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                                        Text(note.content, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // DOCUMENT & RESUME GENERATOR
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PROFESSIONAL RESUME PDF BUILDER", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = roleTitle, onValueChange = { roleTitle = it }, label = { Text("Target Role") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Professional Summary") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = skills, onValueChange = { skills = it }, label = { Text("Skills") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = experience, onValueChange = { experience = it }, label = { Text("Experience") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = education, onValueChange = { education = it }, label = { Text("Education") }, modifier = Modifier.fillMaxWidth())

                        Button(
                            onClick = {
                                viewModel.generateResumePdf(
                                    ResumeInput(fullName, roleTitle, email, phone, summary, skills, experience, education)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianBackground),
                            modifier = Modifier.fillMaxWidth().testTag("generate_resume_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Resume PDF")
                        }
                    }
                }

                // Multi-Format Report Export (PDF / Word / Excel CSV)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REPORT, WORD & EXCEL EXPORTER", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        OutlinedTextField(value = reportTitle, onValueChange = { reportTitle = it }, label = { Text("Document Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = reportBody, onValueChange = { reportBody = it }, label = { Text("Content or rows (one per line)") }, modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { viewModel.generateReportDocument(reportTitle, reportBody, "PDF") }, modifier = Modifier.weight(1f)) {
                                Text("PDF")
                            }
                            FilledTonalButton(onClick = { viewModel.generateReportDocument(reportTitle, reportBody, "WORD") }, modifier = Modifier.weight(1f)) {
                                Text("Word .doc")
                            }
                            FilledTonalButton(onClick = { viewModel.generateReportDocument(reportTitle, reportBody, "EXCEL") }, modifier = Modifier.weight(1f)) {
                                Text("Excel .csv")
                            }
                        }
                    }
                }

                docsState.statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = OnlineEmerald)
                }

                docsState.lastGenerated?.let { generated ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                        border = BorderStroke(1.dp, OnlineEmerald.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Generated: ${generated.fileName}", style = MaterialTheme.typography.titleMedium)
                            generated.previewBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Generated PDF Preview",
                                    modifier = Modifier.fillMaxWidth().height(220.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Button(onClick = { viewModel.shareGeneratedDocument() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Share / Open Document")
                            }
                        }
                    }
                }

                // Pick & Inspect PDF/Text Document
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("READ & SUMMARIZE LOCAL PDF / DOCUMENT", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        OutlinedButton(
                            onClick = { docPicker.launch(arrayOf("application/pdf", "text/plain", "text/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick PDF or Text File")
                        }
                        docsState.pickedDocPreview?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Picked PDF Page 1",
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        docsState.pickedDocSummary?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = AlertAmber)
                        }
                    }
                }
            }

            2 -> {
                // AUTOMATION HISTORY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MULTI-STEP AUTOMATION LOGS", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                    if (logs.isNotEmpty()) {
                        OutlinedButton(onClick = { viewModel.clearAutomationLogs() }) {
                            Text("Clear")
                        }
                    }
                }
                if (logs.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = ArcCyan)
                            Spacer(Modifier.height(6.dp))
                            Text("No automations logged yet. Run a Morning Briefing, Resume PDF, or Media Studio edit to view step-by-step execution records.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    logs.forEach { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                            border = BorderStroke(1.dp, CardBorderSlate)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.workflowName, style = MaterialTheme.typography.titleMedium, color = ArcCyan)
                                    Text(log.status, style = MaterialTheme.typography.labelSmall, color = OnlineEmerald)
                                }
                                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                Text(log.stepsDetail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
