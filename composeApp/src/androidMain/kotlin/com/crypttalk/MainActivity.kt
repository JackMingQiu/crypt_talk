package com.crypttalk

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val environment = AppEnvironment(
            dataDir = File(filesDir, "crypt_talk"),
            copyText = { text ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("CryptTalk", text))
            },
            pasteText = {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                    .orEmpty()
            }
        )
        setContent {
            com.crypttalk.ui.App(environment)
        }
    }
}
