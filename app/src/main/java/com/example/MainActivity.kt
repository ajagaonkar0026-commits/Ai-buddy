package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.JarvisAssistantService
import com.example.ui.JarvisViewModel
import com.example.ui.components.SensitiveActionDialog
import com.example.ui.screens.CommandHubScreen
import com.example.ui.screens.DeviceControlScreen
import com.example.ui.screens.MediaStudioScreen
import com.example.ui.screens.PermissionStatusItem
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksAndDocsScreen
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanContainer
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground

enum class JarvisTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CORE("core", "Core", Icons.Filled.Hub, Icons.Outlined.Hub),
    CONTROL("control", "Control", Icons.Filled.Devices, Icons.Outlined.Devices),
    MEDIA("media", "Studio", Icons.Filled.PhotoFilter, Icons.Outlined.PhotoFilter),
    TASKS("tasks", "Tasks", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private var launchActionState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchActionState.value = intent?.action

        setContent {
            JarvisTheme {
                JarvisRootApp(
                    launchAction = launchActionState.value,
                    onConsumeLaunchAction = { launchActionState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchActionState.value = intent.action
    }
}

@Composable
fun JarvisRootApp(
    launchAction: String?,
    onConsumeLaunchAction: () -> Unit,
    viewModel: JarvisViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(JarvisTab.CORE) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val statusFeedback by viewModel.statusFeedback.collectAsStateWithLifecycle()
    val navRequest by viewModel.navigationRequest.collectAsStateWithLifecycle()

    var permissionVersion by remember { mutableStateOf(0) }
    fun isGranted(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionVersion++
        if (granted) {
            viewModel.refreshTelemetry()
        }
    }

    val micForVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionVersion++
        if (granted) {
            viewModel.startVoiceInput(context as? ComponentActivity)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionVersion++
        if (granted) {
            viewModel.setBackgroundNotificationEnabled(true)
        }
    }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            JarvisAssistantService.ACTION_OPEN_SETTINGS -> {
                currentTab = JarvisTab.SETTINGS
                onConsumeLaunchAction()
            }
            JarvisAssistantService.ACTION_VOICE_CONVERSATION -> {
                currentTab = JarvisTab.CORE
                if (isGranted(Manifest.permission.RECORD_AUDIO)) {
                    viewModel.startVoiceInput(context as? ComponentActivity)
                } else {
                    micForVoiceLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                onConsumeLaunchAction()
            }
        }
    }

    LaunchedEffect(navRequest) {
        when (navRequest) {
            "media" -> currentTab = JarvisTab.MEDIA
            "tasks" -> currentTab = JarvisTab.TASKS
            "control" -> currentTab = JarvisTab.CONTROL
            "settings" -> currentTab = JarvisTab.SETTINGS
            "core" -> currentTab = JarvisTab.CORE
        }
        if (navRequest != null) viewModel.consumeNavigationRequest()
    }

    LaunchedEffect(statusFeedback) {
        statusFeedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusFeedback()
        }
    }

    BackHandler(enabled = currentTab != JarvisTab.CORE) {
        currentTab = JarvisTab.CORE
    }

    val hasMic = remember(permissionVersion) { isGranted(Manifest.permission.RECORD_AUDIO) }
    val hasNotif = remember(permissionVersion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    val permissionList = remember(permissionVersion) {
        buildList {
            add(PermissionStatusItem("Microphone (Voice)", isGranted(Manifest.permission.RECORD_AUDIO)) {
                singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionStatusItem("Notifications (Status Bar)", isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                    singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            add(PermissionStatusItem("Coarse Location (Weather)", isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                singlePermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            })
            add(PermissionStatusItem("Calendar (Morning Briefing)", isGranted(Manifest.permission.READ_CALENDAR)) {
                singlePermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            })
            add(PermissionStatusItem("Contacts (Call/SMS Lookup)", isGranted(Manifest.permission.READ_CONTACTS)) {
                singlePermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            })
            add(PermissionStatusItem("Direct Phone Call", isGranted(Manifest.permission.CALL_PHONE)) {
                singlePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(PermissionStatusItem("Bluetooth Connect", isGranted(Manifest.permission.BLUETOOTH_CONNECT)) {
                    singlePermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                })
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MidnightSurface) {
                        JarvisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyanContainer
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MidnightSurface) {
                        JarvisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyanContainer
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        JarvisTab.CORE -> CommandHubScreen(
                            viewModel = viewModel,
                            hasMicPermission = hasMic,
                            onRequestMicPermission = {
                                micForVoiceLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                        JarvisTab.CONTROL -> DeviceControlScreen(
                            viewModel = viewModel,
                            hasNotificationPermission = hasNotif,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                        JarvisTab.MEDIA -> MediaStudioScreen(viewModel = viewModel)
                        JarvisTab.TASKS -> TasksAndDocsScreen(viewModel = viewModel)
                        JarvisTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            permissions = permissionList
                        )
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        SensitiveActionDialog(
            action = action,
            onConfirm = { viewModel.confirmPendingAction() },
            onDismiss = { viewModel.dismissPendingAction() }
        )
    }
}
