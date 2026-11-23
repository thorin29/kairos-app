package com.kairos.app.ui.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * Places (v0.550; server 0.762+): the web's Places pages — the live map and
 * each person's day — shown in a WebView, so the phone gets the same Google
 * map, tracks and History as the web. The app asks the server for a one-time
 * link (its device token never enters the page); the page's own session lasts
 * 12 hours and is renewed here whenever the server says it has expired.
 * Links away from Places (Google Maps, directions) open in the phone's apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun PlacesScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("loading") } // loading | ok | off | error
    var canGoBack by remember { mutableStateOf(false) }
    var reopen by remember { mutableIntStateOf(0) }
    var renewals by remember { mutableIntStateOf(0) }
    val origin = remember { container.sessionRepository.serverOrigin() }
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(false)
            CookieManager.getInstance().setAcceptCookie(true)
            // Every touch on the page belongs to the page (panning the map,
            // scrolling the list). Without this the menu's swipe-to-open took
            // over any sideways drift and cancelled the page's gesture. The
            // menu still opens from the logo or by swiping the top bar.
            setOnTouchListener { v, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
        }
    }
    fun crumb(what: String) = com.kairos.app.data.diag.Breadcrumbs.drop("places $what")

    // The page asks: notifications for arrive/leave need the phone's permission.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("kairos_places", android.content.Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifications.hasPermission(context) &&
            !prefs.getBoolean("askedNotifications", false)
        ) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun open(to: String = "/places/embed") {
        scope.launch {
            val url = runCatching { container.sessionRepository.placesWebUrl(to = to) }
            crumb(
                when {
                    url.isFailure -> "link failed: ${url.exceptionOrNull()?.javaClass?.simpleName}"
                    url.getOrNull() == null -> "off"
                    else -> "link ok to=$to"
                },
            )
            state = when {
                url.isFailure -> "error"
                url.getOrNull() == null -> "off"
                else -> {
                    webView.loadUrl(url.getOrThrow()!!)
                    "ok"
                }
            }
        }
    }

    LaunchedEffect(reopen) { open() }

    DisposableEffect(Unit) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val same = origin != null && url.toString().startsWith(origin) && (url.path ?: "").startsWith("/places/embed")
                if (same) return false
                if (request.isRedirect || !request.hasGesture()) {
                    // Sent somewhere else without a tap (a sign-in portal in
                    // front of the server, a wrong address): stay here and say so.
                    crumb("redirected to ${url.host}${url.path ?: ""}")
                    state = "blocked"
                    return true
                }
                crumb("open outside ${url.host}")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return true
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                // The page's session expired (or the server restarted): get a
                // fresh link for the page that was open, a few times at most.
                if (request.isForMainFrame) crumb("http ${errorResponse.statusCode} ${request.url.path}")
                if (request.isForMainFrame && errorResponse.statusCode == 401 && renewals < 3) {
                    renewals += 1
                    val path = request.url.path?.takeIf { it.startsWith("/places/embed") && it != "/places/embed/start" }
                    open(to = path ?: "/places/embed")
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                crumb("page ${Uri.parse(url ?: "").path}")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) crumb("load error ${error.errorCode} ${request.url.host}")
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
            }
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Family map") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            when (state) {
                "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                "off" -> Message(text = "Family map isn’t set up yet.", onRetry = { reopen += 1 })
                "error" -> Message(text = "Couldn’t open Family map.", onRetry = { reopen += 1 })
                "blocked" -> Message(
                    text = "Family map was sent to another page instead of opening here.",
                    onRetry = { reopen += 1 },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
