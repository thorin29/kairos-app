package com.kairos.app.data.remote

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Regression guard for the offline fallback. On a 502/503/504 the interceptor
 * falls back to the last cached copy — but it must fully read the live error
 * before starting the cache lookup, or OkHttp throws IllegalStateException
 * ("cannot make a new request because the previous response is still open")
 * and the app crashes. These tests fail if that regresses.
 */
class OfflineInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = Files.createTempDirectory("kairos-cache").toFile()
        client = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 5L * 1024 * 1024))
            .addInterceptor(OfflineInterceptor(isOnline = { true }, queue = null))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun get(): Response =
        client.newCall(Request.Builder().url(server.url("/data")).build()).execute()

    /** 502 with a cached copy → serve the cached copy, no crash. */
    @Test
    fun serves_cached_copy_on_502() {
        // Prime the cache with a copy that is immediately stale, so the next GET
        // revalidates against the network (and receives the 502).
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=0")
                .setBody("cached-data"),
        )
        get().use { first -> assertEquals("cached-data", first.body!!.string()) }

        // Server now "down": the interceptor should return the cached copy.
        server.enqueue(MockResponse().setResponseCode(502).setBody("gateway-error"))
        get().use { res ->
            assertEquals(200, res.code)
            assertEquals("cached-data", res.body!!.string())
        }
    }

    /** 502 with nothing cached → return the error cleanly, no crash. */
    @Test
    fun returns_error_on_502_when_nothing_cached() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("gateway-error"))
        get().use { res ->
            assertEquals(502, res.code)
            // Body must be readable — proves the live response was consumed and
            // rebuilt rather than left dangling.
            assertEquals("gateway-error", res.body!!.string())
        }
        // Reaching here without an exception is the core regression guard.
    }
}
