package com.crypttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crypttalk.AppController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(controller: AppController) {
    var showReset by remember { mutableStateOf(false) }
    val profile = controller.profile

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { controller.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("我的身份", style = MaterialTheme.typography.titleMedium)
                    Text(
                        profile?.username.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "公钥指纹 ${fingerprintOf(controller.myPkHex)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KeyBlock(controller.myPkHex)
                    OutlinedButton(onClick = { controller.copy(controller.myPkHex) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("复制公钥")
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("安全", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PIN 经 SM3 派生 enc_k，再用 SM4-GCM 加密身份私钥。锁定后需要重新输入 PIN 才能解密。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { controller.lock() }) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即锁定")
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("协议说明", style = MaterialTheme.typography.titleMedium)
                    Text("· SM2：身份密钥、临时密钥签名认证、ECDH 密钥协商", style = MaterialTheme.typography.bodySmall)
                    Text("· SM3：PIN 密钥派生、会话密钥扩展、公钥指纹", style = MaterialTheme.typography.bodySmall)
                    Text("· SM4-GCM：私钥存储、会话密钥存储、聊天消息加密", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "· 消息不经过服务器：密文通过任意第三方通道复制粘贴传输",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "危险操作",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "重置会删除本机全部数据：SM2 私钥、联系人、会话密钥与聊天记录，且无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    OutlinedButton(onClick = { showReset = true }) {
                        Text("重置应用", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = "重置应用",
            message = "将删除本机全部数据（包括 SM2 私钥、联系人和聊天记录），且无法恢复。确定继续？",
            confirmLabel = "重置",
            destructive = true,
            onConfirm = {
                showReset = false
                controller.resetApp()
            },
            onDismiss = { showReset = false }
        )
    }
}
