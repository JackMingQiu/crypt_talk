package com.crypttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crypttalk.crypto.Hex
import com.crypttalk.protocol.Protocol
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CodeDialogData(
    val title: String,
    val hint: String,
    val code: String
)

fun formatTimestamp(ts: Long): String =
    Instant.ofEpochSecond(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

fun fingerprintOf(pkHex: String): String =
    runCatching { Protocol.fingerprint(Hex.decode(pkHex)) }.getOrDefault("—")

@Composable
fun FingerprintText(pkHex: String, modifier: Modifier = Modifier) {
    Text(
        text = "指纹 ${fingerprintOf(pkHex)}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun KeyBlock(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun CodeDialog(
    data: CodeDialogData,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = data.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = data.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(data.code) }) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确认",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun StatusPill(text: String, container: Color, content: Color) {
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}
