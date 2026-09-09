package com.kairos.app.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.enroll.EnrollScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class AuthStep { SignIn, Code }

/**
 * The unauthenticated flow: sign in (username/password) then pair with a code —
 * the layered login+code path. The "child device" link jumps straight to the
 * code with no login, for passwordless kids a parent provisions. Which path was
 * taken is implicit: signing in leaves a login proof in the session that enroll
 * sends; the child path leaves none, and the server enforces the rule.
 */
@Composable
fun AuthFlow() {
    var step by remember { mutableStateOf(AuthStep.SignIn) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }
    var forgotText by remember { mutableStateOf("") }
    var forgotSent by remember { mutableStateOf(false) }
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    when (step) {
        AuthStep.SignIn -> SignInScreen(
            onSignedIn = { step = AuthStep.Code },
            onUseCode = { step = AuthStep.Code },
            onChangeServer = {
                scope.launch { container.sessionRepository.changeServer() }
            },
            onHaveInvite = { showPaste = true },
            onForgot = {
                forgotText = ""
                forgotSent = false
                showForgot = true
            },
        )
        AuthStep.Code -> EnrollScreen(
            onNeedSignIn = { step = AuthStep.SignIn },
            onBack = { step = AuthStep.SignIn },
        )
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("Enter your invitation code") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("Invitation code or link") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = pasteText.trim()
                    val token = if (raw.contains("token=")) {
                        raw.substringAfter("token=").substringBefore("&").trim()
                    } else {
                        raw
                    }
                    showPaste = false
                    pasteText = ""
                    if (token.isNotBlank()) container.pendingJoinToken.value = token
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showPaste = false }) { Text("Cancel") }
            },
        )
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text(if (forgotSent) "Check your email" else "Reset your password") },
            text = {
                if (forgotSent) {
                    Text(
                        "If that account has an email on file, a reset link is on its " +
                            "way. Open it on this phone to choose a new password.",
                    )
                } else {
                    OutlinedTextField(
                        value = forgotText,
                        onValueChange = { forgotText = it },
                        label = { Text("Your name or email") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                if (forgotSent) {
                    TextButton(onClick = { showForgot = false }) { Text("Done") }
                } else {
                    TextButton(onClick = {
                        val id = forgotText.trim()
                        if (id.isNotBlank()) {
                            scope.launch {
                                runCatching { container.sessionRepository.requestReset(id) }
                                forgotSent = true
                            }
                        }
                    }) { Text("Send reset link") }
                }
            },
            dismissButton = {
                if (!forgotSent) {
                    TextButton(onClick = { showForgot = false }) { Text("Cancel") }
                }
            },
        )
    }
}
