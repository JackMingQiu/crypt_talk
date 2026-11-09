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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crypttalk.AppController

@Composable
fun SetupScreen(controller: AppController) {
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "CryptTalk",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "基于 SM2 / SM3 / SM4 的本地加密聊天工具",
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
                    Text("创建身份", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "应用会在本机生成一对 SM2 身份密钥。你的 PIN 经 SM3 派生出 SM4 密钥，用于加密保存身份私钥；私钥不会离开本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(32) },
                        label = { Text("用户名") },
                        placeholder = { Text("对方会看到这个名字") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup-username")
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.take(16) },
                        label = { Text("PIN（6-16 位）") },
                        supportingText = { Text("支持数字、大小写字母和标点符号，不含空格") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup-pin")
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it.take(16) },
                        label = { Text("确认 PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup-pin-confirm")
                    )

                    if (error != null) {
                        Text(
                            error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            error = controller.setup(username, pin, confirmPin)
                            busy = false
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup-submit")
                    ) {
                        Text(if (busy) "正在生成密钥…" else "创建身份")
                    }

                    Text(
                        "PIN 用于解密身份私钥，忘记后无法恢复，请务必牢记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
