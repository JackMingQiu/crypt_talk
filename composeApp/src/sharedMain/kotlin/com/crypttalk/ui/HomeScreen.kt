package com.crypttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crypttalk.AppController
import com.crypttalk.SessionStatus
import com.crypttalk.data.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(controller: AppController) {
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CryptTalk") },
                actions = {
                    IconButton(onClick = { controller.openSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { controller.lock() }) {
                        Icon(Icons.Default.Lock, contentDescription = "锁定")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IdentityCard(controller)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "聊天",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("home-add")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建聊天")
                }
            }

            if (controller.contacts.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("还没有联系人", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "点击“新建聊天”，输入对方的用户名和 SM2 身份公钥。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                controller.contacts.forEach { contact ->
                    ContactCard(
                        contact = contact,
                        controller = controller,
                        onDelete = { contactToDelete = contact }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            controller = controller,
            onDismiss = { showAddDialog = false }
        )
    }

    contactToDelete?.let { contact ->
        ConfirmDialog(
            title = "删除联系人",
            message = "删除「${contact.name}」会同时删除本机与对方的聊天记录和会话密钥，且无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                contactToDelete = null
                controller.removeContact(contact.id)?.let { controller.showNotice(it) }
            },
            onDismiss = { contactToDelete = null }
        )
    }
}

@Composable
private fun IdentityCard(controller: AppController) {
    val profile = controller.profile ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "我的身份",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        profile.username,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(onClick = { controller.copy(controller.myPkHex) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制公钥")
                }
            }
            Text(
                "公钥指纹 ${fingerprintOf(controller.myPkHex)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            KeyBlock(controller.myPkHex)
            Text(
                "把这个公钥发给对方，对方添加后即可与你建立会话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    controller: AppController,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val active = controller.activeSession(contact.id)
    val pending = controller.pendingSession(contact.id)
    val statusText = when {
        active != null -> "已建立会话"
        pending != null -> "等待接受"
        else -> "未建立会话"
    }
    val pillContainer = when {
        active != null -> MaterialTheme.colorScheme.primaryContainer
        pending != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val pillContent = when {
        active != null -> MaterialTheme.colorScheme.onPrimaryContainer
        pending != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { controller.openChat(contact.id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        contact.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FingerprintText(contact.pkHex)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(statusText, pillContainer, pillContent)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("删除联系人") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddContactDialog(controller: AppController, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pkHex by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建聊天") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add-name")
                )
                OutlinedTextField(
                    value = pkHex,
                    onValueChange = { pkHex = it },
                    label = { Text("身份公钥") },
                    placeholder = { Text("04 开头的十六进制 SM2 公钥") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = { Text("由对方在“我的身份”中复制") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add-pk")
                )
                if (error != null) {
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val err = controller.addContact(name, pkHex)
                    if (err == null) {
                        controller.showNotice("联系人已添加")
                        onDismiss()
                    } else {
                        error = err
                    }
                },
                modifier = Modifier.testTag("add-confirm")
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
