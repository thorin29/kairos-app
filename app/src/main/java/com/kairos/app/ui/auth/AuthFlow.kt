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
        )
        AuthStep.Code -> EnrollScreen(
            onNeedSignIn = { step = AuthStep.SignIn },
            onBack = { step = AuthStep.SignIn },
        )
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("Paste your invite") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("Invite link") },
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
}
