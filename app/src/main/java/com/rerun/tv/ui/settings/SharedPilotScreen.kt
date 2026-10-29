package com.rerun.tv.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.theme.Retro
import com.rerun.tv.data.groupedSharedCode

@Composable
fun SharedPilotScreen(controller: TvController) {
    val status by controller.sharedPilot.collectAsState()
    val update by controller.lineupUpdate.collectAsState()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val first = remember { FocusRequester() }
    var code by rememberSaveable { mutableStateOf("") }
    var joining by rememberSaveable { mutableStateOf(false) }
    var entryError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCode by rememberSaveable { mutableStateOf<String?>(null) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val codeStyle = Retro.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 24.sp,
        fontFeatureSettings = "'zero'", shadow = null)
    fun clipboardAction(operation: () -> Unit) {
        try { operation() } catch (_: Exception) { localMessage = "Clipboard unavailable. Enter the code manually." }
    }
    BackHandler {
        if (joining) joining = false else controller.go(Screen.Settings)
    }
    LaunchedEffect(Unit) { controller.loadSharedPilot() }
    LaunchedEffect(status.code, update.busy, update.error) {
        if (status.code == pendingCode || (!update.busy && update.error != null)) pendingCode = null
    }
    if (!joining) TvStage("BACK settings") {
        Column(Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText("SHARED SCHEDULING · OPT-IN PILOT", style = Retro.GuideNumber)
            TvButton("Join code", {
                localMessage = null
                entryError = null
                joining = true
            }, modifier = Modifier.focusRequester(first), enabled = !update.busy)
            BasicText("Your sharing code", style = Retro.Body)
            BasicText((pendingCode ?: status.code)?.let(::groupedSharedCode) ?: "Off", style = codeStyle,
                modifier = Modifier.testTag("shared-code-display"))
            if (update.busy || pendingCode != null) {
                BasicText("Joining/updating schedule — please wait. The code is not active until saved.",
                    style = Retro.Body, modifier = Modifier.testTag("shared-join-progress"))
            }
            BasicText("Codes use the digit 0, never the letter O. O is also accepted as 0 when entering a code.",
                style = Retro.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Copy code", {
                    clipboardAction {
                        status.code?.let { clipboard.setPrimaryClip(ClipData.newPlainText("Rerun shared schedule", groupedSharedCode(it))) }
                        localMessage = if (status.code == null) "Generate a code first." else "Code copied."
                    }
                }, enabled = !update.busy && pendingCode == null)
                TvButton("Disable", { localMessage = null; controller.setSharedPilot() }, enabled = !update.busy)
                TvButton("Refresh status", { controller.loadSharedPilot() })
            }
            (update.error ?: localMessage ?: update.message)?.let { BasicText(it, style = Retro.Body) }
            BasicText(
                "Stock video channels 100–199 only. Custom, music, edited rules/order and time-block channels are excluded. " +
                    "Same code requires matching channel numbers, regular Plex content and rules. Compare channel fingerprints below; " +
                "different Plex item/show IDs or durations can change the programs. This is not a content-sharing service.",
                style = Retro.Caption,
            )
            BasicText(
                "Fixed three-hour UTC blocks; episodes from the same show stay together. Overlong episodes and movies " +
                    "are excluded; movies may lose up to two minutes at block end. Commercials are independently randomized " +
                    "on each device and never delay programs. Music stays independent.",
                style = Retro.Caption,
            )
            BasicText(
                "Joining refreshes Plex first. Stale libraries refresh at most daily when a visible/tuned channel needs " +
                    "more schedule. Playback uses the saved snapshot while refreshing; failures keep it and appear in Settings. " +
                    "Visible/tuned channels build 48 hours lazily. Existing programs and their break finish " +
                    "before the next safe UTC block; a waiting card can bridge the gap. QR and system sharing are not included.",
                style = Retro.Caption,
            )
            BasicText("Create a new schedule to share", style = Retro.Body)
            TvButton("Generate & join", { localMessage = null; controller.setSharedPilot(generate = true) },
                enabled = !update.busy)
            if (status.effectiveFromMs > 0) BasicText(
                "Requested UTC boundary: ${java.time.Instant.ofEpochMilli(status.effectiveFromMs)}. Per-channel cutovers may be later.",
                style = Retro.Caption,
            )
            status.channels.forEach { BasicText(it, style = Retro.Caption) }
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }
    if (joining) {
        val input = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        fun dismiss() {
            keyboard?.hide()
            joining = false
        }
        TvStage("BACK cancel") {
            Column(
                Modifier.fillMaxWidth()
                    .background(Retro.GuideBlueDeep).border(2.dp, Retro.Cyan)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BasicText("Join shared schedule", style = Retro.GuideNumber)
                BasicText("Enter the code from the other device. Hyphens and lowercase are accepted.",
                    style = Retro.Caption)
                BasicTextField(code, {
                    code = it.take(128)
                    entryError = null
                }, singleLine = true, textStyle = codeStyle.copy(color = Retro.Cyan),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Retro.Yellow)
                        .padding(12.dp).focusRequester(input).testTag("shared-code-input"))
                entryError?.let { BasicText(it, style = Retro.Body) }
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TvButton("Paste", {
                        try {
                            code = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)
                                ?.toString()?.trim().orEmpty().take(128)
                            entryError = if (code.isBlank()) "Clipboard has no code." else null
                        } catch (_: Exception) {
                            entryError = "Clipboard unavailable. Enter the code manually."
                        }
                    })
                    TvButton("Confirm join", {
                        val error = try {
                            com.rerun.tv.data.validateSharedCode(code, System.currentTimeMillis())
                            null
                        } catch (e: IllegalArgumentException) {
                            e.message ?: "Invalid shared scheduling code"
                        }
                        if (error != null) entryError = error else {
                            localMessage = null
                            pendingCode = com.rerun.tv.data.validateSharedCode(code, System.currentTimeMillis()).encode()
                            controller.setSharedPilot(code)
                            dismiss()
                        }
                    }, enabled = !update.busy && code.isNotBlank())
                    TvButton("Cancel", { dismiss() })
                }
            }
            LaunchedEffect(Unit) {
                input.requestFocus()
            }
        }
    }
}
