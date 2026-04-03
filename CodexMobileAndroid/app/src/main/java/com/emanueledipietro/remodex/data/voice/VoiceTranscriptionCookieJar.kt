package com.emanueledipietro.remodex.data.voice

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface VoiceTranscriptionCookieStore {
    fun load(url: String): String?

    fun save(url: String, cookie: String)
}

class AndroidWebViewCookieStore : VoiceTranscriptionCookieStore {
    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    override fun load(url: String): String? {
        return cookieManager.getCookie(url)?.trim()?.takeIf(String::isNotEmpty)
    }

    override fun save(url: String, cookie: String) {
        cookieManager.setCookie(url, cookie)
        cookieManager.flush()
    }
}

class VoiceTranscriptionCookieJar(
    private val cookieStore: VoiceTranscriptionCookieStore,
) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = cookieStore.load(url.toString()).orEmpty()
        if (cookieHeader.isBlank()) {
            return emptyList()
        }

        return cookieHeader
            .split(';')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                val separatorIndex = trimmed.indexOf('=')
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }

                val name = trimmed.substring(0, separatorIndex).trim()
                val value = trimmed.substring(separatorIndex + 1).trim()
                if (name.isEmpty() || value.isEmpty()) {
                    return@mapNotNull null
                }

                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            cookieStore.save(url.toString(), cookie.toString())
        }
    }
}
