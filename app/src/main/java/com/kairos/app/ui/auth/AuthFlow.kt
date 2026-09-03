package com.kairos.app.ui.auth

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
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    when (step) {
        AuthStep.SignIn -> SignInScreen(
            onSignedIn = { step = AuthStep.Code },
            onUseCode = { step = AuthStep.Code },
            onChangeServer = {
                scope.launch { container.sessionRepository.changeServer() }
            },
        )
        AuthStep.Code -> EnrollScreen(
            onNeedSignIn = { step = AuthStep.SignIn },
            onBack = { step = AuthStep.SignIn },
        )
    }
}
