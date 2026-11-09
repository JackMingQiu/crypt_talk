package com.crypttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crypttalk.AppController
import com.crypttalk.AppEnvironment
import com.crypttalk.Screen

@Composable
fun App(env: AppEnvironment) {
    val controller = remember { AppController(env) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = controller.notice

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            controller.clearNotice()
        }
    }

    CryptTalkTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    controller.startupError != null ->
                        StartupErrorScreen(controller.startupError.orEmpty(), controller)
                    controller.profile == null -> SetupScreen(controller)
                    controller.locked -> UnlockScreen(controller)
                    controller.screen == Screen.Settings -> SettingsScreen(controller)
                    controller.screen == Screen.Chat -> ChatScreen(controller)
                    else -> HomeScreen(controller)
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StartupErrorScreen(message: String, controller: AppController) {
    var showReset by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Text("无法读取本地数据", style = MaterialTheme.typography.headlineSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                "本地资料文件可能已损坏。你可以重置应用并重新初始化，重置会删除本机全部私钥、联系人和聊天记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { showReset = true }) { Text("重置应用") }
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
