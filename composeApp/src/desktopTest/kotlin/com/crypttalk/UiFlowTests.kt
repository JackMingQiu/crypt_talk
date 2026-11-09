package com.crypttalk

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.crypttalk.crypto.Hex
import com.crypttalk.crypto.Sm2
import com.crypttalk.ui.App
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class UiFlowTests {
    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "crypttalk-uitest-${System.nanoTime()}").also { it.mkdirs() }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun setupAddContactAndStartSession() = runComposeUiTest {
        val dir = tempDir()
        val env = AppEnvironment(dataDir = dir, copyText = {}, pasteText = { "" })

        setContent { App(env) }

        onNodeWithTag("setup-submit").assertExists()

        onNodeWithTag("setup-username").performTextInput("alice")
        onNodeWithTag("setup-pin").performTextInput("Aa123456!")
        onNodeWithTag("setup-pin-confirm").performTextInput("Aa123456!")
        onNodeWithTag("setup-submit").performClick()
        waitForIdle()

        onNodeWithText("我的身份").assertExists()
        onAllNodesWithText("alice").onFirst().assertExists()

        onNodeWithTag("home-add").performClick()
        waitForIdle()
        onNodeWithTag("add-name").assertExists()

        val bobPk = Hex.encode(Sm2.publicKeyBytes(Sm2.generateKeyPair().q))
        onNodeWithTag("add-name").performTextInput("bob")
        onNodeWithTag("add-pk").performTextInput(bobPk)
        onNodeWithTag("add-confirm").performClick()
        waitForIdle()

        onAllNodesWithText("bob").onFirst().assertExists()
        onAllNodesWithText("bob").onFirst().performClick()
        waitForIdle()

        onNodeWithText("尚未建立会话").assertExists()
        onNodeWithText("发起会话").performClick()
        waitForIdle()

        onNodeWithText("会话邀请码").assertExists()

        val stored = File(dir, "profile.json")
        assertEquals(true, stored.exists())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockAfterLock() = runComposeUiTest {
        val dir = tempDir()
        val controller = AppController(AppEnvironment(dir, {}, { "" }))
        assertEquals(null, controller.setup("alice", "Aa123456!", "Aa123456!"))
        controller.lock()

        setContent { App(AppEnvironment(dir, {}, { "" })) }

        onNodeWithText("输入 PIN 解锁").assertExists()
        onNodeWithTag("unlock-pin").performTextInput("wrong-pin")
        onNodeWithTag("unlock-submit").performClick()
        waitForIdle()
        onNodeWithText("PIN 错误").assertExists()

        onNodeWithTag("unlock-pin").performTextClearance()
        onNodeWithTag("unlock-pin").performTextInput("Aa123456!")
        onNodeWithTag("unlock-submit").performClick()
        waitForIdle()
        onNodeWithText("我的身份").assertExists()
    }
}
