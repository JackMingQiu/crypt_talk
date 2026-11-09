package com.crypttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crypttalk.AppController
import com.crypttalk.data.StoredMessage
import com.crypttalk.data.SessionRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(controller: AppController) {
    val contact = controller.currentContactId?.let { controller.contact(it) }
    if (contact == null) {
        LaunchedEffect(Unit) { controller.goHome() }
        return
    }

    val active = controller.activeSession(contact.id)
    val pending = controller.pendingSession(contact.id)
    val messageList = controller.messagesFor(contact.id)
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var codeDialog by remember { mutableStateOf<CodeDialogData?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var showNewSessionConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(messageList.lastIndex)
        }
    }

    fun startSession() {
        val result = controller.startSession(contact.id)
        if (result.error != null) {
            controller.showNotice(result.error)
        } else {
            codeDialog = CodeDialogData(
                title = "会话邀请码",
                hint = "请通过任意第三方通道把邀请码发给对方。对方粘贴后会生成接受码，你再把接受码粘贴回来，会话即可建立。",
                code = result.inviteCode.orEmpty()
            )
        }
    }

    fun openPasteDialog() {
        pasteText = controller.paste()
        pasteError = null
        showPasteDialog = true
    }

    val statusLabel = when {
        active != null -> "会话已建立"
        pending != null -> "等待对方接受"
        else -> "未建立会话"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { controller.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建会话") },
                                onClick = {
                                    menuExpanded = false
                                    if (active != null || pending != null) {
                                        showNewSessionConfirm = true
                                    } else {
                                        startSession()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制我的公钥") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    controller.copy(controller.myPkHex)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除联系人") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            SessionBanner(
                active = active,
                pending = pending,
                onStart = { if (active != null || pending != null) showNewSessionConfirm = true else startSession() },
                onCopyPending = {
                    val code = pending?.lastCode.orEmpty()
                    if (code.isEmpty()) {
                        controller.showNotice("邀请码不可用，请重新发起会话")
                    } else {
                        codeDialog = CodeDialogData(
                            title = "会话邀请码",
                            hint = "请把邀请码发送给对方，并把对方返回的接受码粘贴回来。",
                            code = code
                        )
                    }
                },
                onPaste = { openPasteDialog() }
            )

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messageList.isEmpty()) {
                    item {
                        Text(
                            "还没有消息。建立会话后，输入明文并点击“加密并复制”，再把密文通过第三方通道发给对方。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(messageList, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        controller = controller,
                        onShowCode = { data -> codeDialog = data }
                    )
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (active == null) {
                    Text(
                        "建立会话后才能加密发送消息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("输入明文消息…") },
                        maxLines = 4,
                        enabled = active != null,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val result = controller.sendMessage(contact.id, input)
                            if (result.error != null) {
                                controller.showNotice(result.error)
                            } else {
                                input = ""
                                codeDialog = CodeDialogData(
                                    title = "密文消息",
                                    hint = "密文已复制到剪贴板。请通过第三方通道发送给对方，对方粘贴进 CryptTalk 即可解密。",
                                    code = result.code.orEmpty()
                                )
                            }
                        },
                        enabled = active != null && input.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("加密并复制")
                    }
                }
                OutlinedButton(onClick = { openPasteDialog() }) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("粘贴并处理")
                }
            }
        }
    }

    codeDialog?.let { data ->
        CodeDialog(
            data = data,
            onCopy = { controller.copy(it) },
            onDismiss = { codeDialog = null }
        )
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("粘贴并处理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "粘贴对方发来的邀请码、接受码或聊天密文，程序会自动识别类型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = {
                            pasteText = it
                            pasteError = null
                        },
                        placeholder = { Text("CTK1:…") },
                        minLines = 5,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pasteError != null) {
                        Text(
                            pasteError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = controller.handlePasted(contact.id, pasteText)
                    when {
                        result.error != null -> pasteError = result.error
                        result.replyCode != null -> {
                            showPasteDialog = false
                            codeDialog = CodeDialogData(
                                title = "会话接受码",
                                hint = "会话已建立。请把接受码发送给对方，对方粘贴后即可开始聊天。",
                                code = result.replyCode
                            )
                        }
                        else -> {
                            showPasteDialog = false
                            controller.showNotice(result.message ?: "已处理")
                        }
                    }
                }) { Text("处理") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pasteText = controller.paste()
                        pasteError = null
                    }) { Text("从剪贴板粘贴") }
                    TextButton(onClick = { showPasteDialog = false }) { Text("取消") }
                }
            }
        )
    }

    if (showNewSessionConfirm) {
        ConfirmDialog(
            title = "新建会话",
            message = "新建会话会结束当前会话并重新协商一次性密钥。历史消息仍可解密查看。确定继续？",
            confirmLabel = "新建",
            onConfirm = {
                showNewSessionConfirm = false
                startSession()
            },
            onDismiss = { showNewSessionConfirm = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除联系人",
            message = "删除「${contact.name}」会同时删除本机与对方的聊天记录和会话密钥，且无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                controller.removeContact(contact.id)?.let { controller.showNotice(it) }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun SessionBanner(
    active: SessionRecord?,
    pending: SessionRecord?,
    onStart: () -> Unit,
    onCopyPending: () -> Unit,
    onPaste: () -> Unit
) {
    if (active != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = "会话已建立",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(10.dp))
            FingerprintText(active.peerEphPkHex, Modifier.weight(1f))
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (pending != null) "等待对方接受邀请" else "尚未建立会话",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                if (pending != null) {
                    "把邀请码发给对方，收到对方的接受码后粘贴进来即可建立会话。"
                } else {
                    "会话采用一次性 SM2 临时密钥协商，每段对话都重新生成密钥。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending != null) {
                    Button(onClick = onCopyPending) { Text("复制邀请码") }
                    OutlinedButton(onClick = onPaste) { Text("粘贴接受码") }
                    TextButton(onClick = onStart) { Text("重新发起") }
                } else {
                    Button(onClick = onStart) { Text("发起会话") }
                    OutlinedButton(onClick = onPaste) { Text("粘贴邀请码") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: StoredMessage,
    controller: AppController,
    onShowCode: (CodeDialogData) -> Unit
) {
    val text = controller.messageText(message)

    if (message.direction == "system") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val outgoing = message.direction == "outgoing"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Surface(
                color = if (outgoing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (outgoing) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTimestamp(message.ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (outgoing) {
                    TextButton(onClick = {
                        onShowCode(
                            CodeDialogData(
                                title = "密文消息",
                                hint = "可再次复制这段密文，通过第三方通道发送给对方。",
                                code = message.envelope
                            )
                        )
                    }) {
                        Text("密文", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
