package com.crypttalk

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.crypttalk.ui.App
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

fun main() = application {
    val environment = AppEnvironment(
        dataDir = File(System.getProperty("user.home"), ".crypt_talk"),
        copyText = { text ->
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        },
        pasteText = {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
            }.getOrDefault("")
        }
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "CryptTalk",
        state = rememberWindowState(width = 1100.dp, height = 780.dp)
    ) {
        App(environment)
    }
}
