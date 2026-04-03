package com.emanueledipietro.remodex.data.voice

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTranscriptionCookieJarTest {
    @Test
    fun `loadForRequest returns parsed cookies from the backing store`() {
        val store = FakeVoiceTranscriptionCookieStore(
            cookiesByUrl = mutableMapOf(
                "https://chatgpt.com/backend-api/transcribe" to "__cf_bm=abc; _cfuvid=def",
            ),
        )
        val jar = VoiceTranscriptionCookieJar(store)

        val cookies = jar.loadForRequest("https://chatgpt.com/backend-api/transcribe".toHttpUrl())

        assertEquals(listOf("__cf_bm", "_cfuvid"), cookies.map { cookie: Cookie -> cookie.name })
        assertEquals(listOf("abc", "def"), cookies.map { cookie: Cookie -> cookie.value })
    }

    @Test
    fun `saveFromResponse persists set-cookie values into the backing store`() {
        val store = FakeVoiceTranscriptionCookieStore()
        val jar = VoiceTranscriptionCookieJar(store)
        val url = "https://chatgpt.com/backend-api/transcribe".toHttpUrl()

        jar.saveFromResponse(
            url = url,
            cookies = listOf(
                Cookie.Builder()
                    .name("__cf_bm")
                    .value("token")
                    .domain("chatgpt.com")
                    .path("/")
                    .build(),
            ),
        )

        assertEquals(url.toString(), store.savedCookies.single().first)
        assertTrue(store.savedCookies.single().second.startsWith("__cf_bm=token"))
    }
}

private class FakeVoiceTranscriptionCookieStore(
    private val cookiesByUrl: MutableMap<String, String> = mutableMapOf(),
) : VoiceTranscriptionCookieStore {
    val savedCookies = mutableListOf<Pair<String, String>>()

    override fun load(url: String): String? = cookiesByUrl[url]

    override fun save(url: String, cookie: String) {
        savedCookies += url to cookie
    }
}
