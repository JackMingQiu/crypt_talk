package com.crypttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.crypttalk.AppController

@Composable
fun UnlockScreen(controller: AppController) {
    val profile = controller.profile
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showReset by remember { mutableStateOf(false) }

    fun attemptUnlock() {
        error = controller.unlock(pin)
        if (error == null) pin = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "CryptTalk",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "欢迎回来，${profile?.username.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("输入 PIN 解锁", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.take(16) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { attemptUnlock() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock-pin")
                    )

                    if (error != null) {
                        Text(
                            error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = { attemptUnlock() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock-submit")
                    ) {
                        Text("解锁")
                    }

                    TextButton(
                        onClick = { showReset = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("忘记 PIN？重置应用", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = "重置应用",
            message = "重置会删除本机全部数据（SM2 私钥、联系人、聊天记录），且无法恢复。确定继续？",
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
