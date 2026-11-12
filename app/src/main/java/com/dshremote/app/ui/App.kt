package com.dshremote.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dshremote.app.DshViewModel
import com.dshremote.app.Screen
import com.dshremote.app.UiState
import com.dshremote.app.data.ApprovalCard
import com.dshremote.app.data.ChatItem
import com.dshremote.app.data.ChatRole
import com.dshremote.app.data.QuestionCard
import com.dshremote.app.data.SessionSummary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DshApp(viewModel: DshViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    when (ui.screen) {
        Screen.PAIR -> PairScreen(ui, viewModel)
        Screen.SESSIONS -> SessionsScreen(ui, viewModel)
        Screen.CHAT -> ChatScreen(ui, viewModel)
        Screen.SETTINGS -> SettingsScreen(ui, viewModel)
    }
}

@Composable
private fun PairScreen(ui: UiState, viewModel: DshViewModel) {
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scanOptions = remember {
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("扫描 DSH 配对二维码")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.pair(host, token, it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scanLauncher.launch(scanOptions)
    }

    fun launchScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanLauncher.launch(scanOptions)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(18.dp).size(38.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "连接 DeepSeek Harness",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "手机扫码后即可同步电脑上的全部项目和会话，并直接远程操作。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("电脑局域网地址") },
            placeholder = { Text("192.168.1.23:3080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("邀请码 / 配对令牌") },
            placeholder = { Text("扫码会自动填入，也可以手动粘贴") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { launchScanner() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("扫码配对")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { viewModel.pair(host, token, "") },
            enabled = token.isNotBlank() && !ui.connecting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Link, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (ui.connecting) "正在连接…" else "用邀请码连接")
        }

        if (ui.connecting) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        }
        ui.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(message)
        }

        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "在电脑上打开 DeepSeek Harness → 侧栏底部「配对 iPhone」→ 扫描二维码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "手动连接时，地址填电脑的局域网 IP（不要填 127.0.0.1），端口默认 3080。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsScreen(ui: UiState, viewModel: DshViewModel) {
    var showNew by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text("DSH Remote", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (ui.connected) "已连接电脑" else "未连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ui.connected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSessions() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { viewModel.showSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话")
            }
        },
    ) { padding ->
        if (ui.sessions.isEmpty()) {
            EmptyState(
                title = "还没有项目会话",
                body = "先在电脑上打开一个项目，或在右下角新建一条会话。",
                modifier = Modifier.padding(padding),
            )
        } else {
            val groups = ui.sessions.groupBy { it.cwd ?: "未命名项目" }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (cwd, sessions) ->
                    item(key = "header-$cwd") {
                        WorkspaceHeader(cwd = cwd, count = sessions.size)
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(session = session, onClick = { viewModel.openSession(session) })
                    }
                }
            }
        }
    }
    if (showNew) {
        NewSessionDialog(
            defaultCwd = ui.sessions.firstOrNull()?.cwd.orEmpty(),
            onDismiss = { showNew = false },
            onCreate = {
                showNew = false
                viewModel.createSession(it)
            },
        )
    }
    ui.error?.let { message ->
        ErrorDialog(message = message, onDismiss = { viewModel.clearError() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(ui: UiState, viewModel: DshViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var answerTarget by remember { mutableStateOf<QuestionCard?>(null) }
    val listState = rememberLazyListState()
    val total = ui.items.size + ui.approvals.size + ui.questions.size
    LaunchedEffect(total, ui.agentRunning) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToSessions() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            ui.current?.title?.ifBlank { "未命名会话" } ?: "会话",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            ui.current?.cwd.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (ui.agentRunning) {
                        IconButton(onClick = { viewModel.interrupt() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "中断", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                value = input,
                onValueChange = { input = it },
                enabled = ui.connected && !ui.connecting,
                onSend = {
                    viewModel.sendPrompt(input)
                    input = ""
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.items, key = { it.id }) { item ->
                ChatBubble(item)
            }
            items(ui.approvals, key = { "approval-${it.id}" }) { card ->
                ApprovalCardView(
                    card = card,
                    onAllow = { viewModel.resolveApproval(card.id, true) },
                    onDeny = { viewModel.resolveApproval(card.id, false) },
                )
            }
            items(ui.questions, key = { "question-${it.id}" }) { card ->
                QuestionCardView(
                    card = card,
                    onAnswer = { selected, custom -> viewModel.answerQuestion(card, selected, custom) },
                    onCustom = { answerTarget = card },
                )
            }
            if (ui.agentRunning) {
                item(key = "running") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "电脑正在处理…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    answerTarget?.let { card ->
        AnswerDialog(
            card = card,
            onDismiss = { answerTarget = null },
            onAnswer = {
                answerTarget = null
                viewModel.answerQuestion(card, emptyList(), it)
            },
        )
    }
    ui.error?.let { message ->
        ErrorDialog(message = message, onDismiss = { viewModel.clearError() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(ui: UiState, viewModel: DshViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToSessions() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("连接设置", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(
                title = "连接状态",
                rows = listOf(
                    "状态" to if (ui.connected) "已连接" else "未连接",
                    "服务器" to (ui.wsUrl ?: "未配置"),
                    "设备 ID" to (ui.deviceId ?: "未配对"),
                ),
            )
            InfoCard(
                title = "安全提示",
                rows = listOf(
                    "传输" to "局域网 WebSocket（ws://）",
                    "建议" to "只在可信 Wi-Fi 下使用；不要暴露到公网",
                ),
            )
            Button(
                onClick = { viewModel.reconnect() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新连接")
            }
            OutlinedButton(
                onClick = { viewModel.unpair() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("取消配对", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("给电脑发消息…") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSend() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(46.dp),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem) {
    when (item.role) {
        ChatRole.USER -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                modifier = Modifier.fillMaxWidth(0.86f),
            ) {
                Text(
                    item.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ChatRole.ASSISTANT -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                Text(
                    item.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ChatRole.TOOL -> Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    item.label ?: "工具",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ChatRole.SYSTEM -> Text(
            item.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ApprovalCardView(
    card: ApprovalCard,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(card.title, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("允许一次")
                }
                OutlinedButton(onClick = onDeny, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("拒绝")
                }
            }
        }
    }
}

@Composable
private fun QuestionCardView(
    card: QuestionCard,
    onAnswer: (List<String>, String?) -> Unit,
    onCustom: () -> Unit,
) {
    var selected by remember(card.id) { mutableStateOf(setOf<String>()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(card.title, fontWeight = FontWeight.SemiBold)
            if (card.detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    card.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.options.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                card.options.forEach { option ->
                    val isSelected = option.label in selected
                    OutlinedButton(
                        onClick = {
                            if (card.multiSelect) {
                                selected = if (isSelected) selected - option.label else selected + option.label
                            } else {
                                onAnswer(listOf(option.label), null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (isSelected) "✓ ${option.label}" else option.label)
                    }
                    if (!option.description.isNullOrBlank()) {
                        Text(
                            option.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                        )
                    }
                }
                if (card.multiSelect) {
                    Button(
                        onClick = { if (selected.isNotEmpty()) onAnswer(selected.toList(), null) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("提交")
                    }
                }
                TextButton(onClick = onCustom) {
                    Text("其他回答…")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCustom, shape = RoundedCornerShape(12.dp)) {
                    Text("回答")
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(cwd: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                cwd.substringAfterLast('/').ifBlank { cwd },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                cwd,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title.ifBlank { if (session.blank) "空白会话" else "未命名会话" },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.agentRunning) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                ) {
                    Text(
                        "运行中",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
                Row {
                    Text(
                        row.first,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp),
                    )
                    Text(
                        row.second,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Computer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        title = { Text("出错了") },
        text = { Text(message) },
    )
}

@Composable
private fun NewSessionDialog(
    defaultCwd: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var cwd by remember { mutableStateOf(defaultCwd) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column {
                Text(
                    "填写电脑上的工作区绝对路径，例如 /Users/you/project。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text("工作区路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(cwd) },
                enabled = cwd.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AnswerDialog(
    card: QuestionCard,
    onDismiss: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var answer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.title) },
        text = {
            Column {
                Text(
                    card.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("你的回答") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(answer) }, enabled = answer.isNotBlank()) {
                Text("提交")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
