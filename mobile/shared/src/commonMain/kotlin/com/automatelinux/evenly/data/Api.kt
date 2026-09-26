package com.automatelinux.evenly.data

import com.automatelinux.evenly.data.model.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Where the last good GET responses are kept so the app opens instantly and works offline. */
interface ResponseCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class ApiException(message: String, val status: Int? = null) : Exception(message)

expect fun createHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): HttpClient

class Api(baseUrl: String, private val token: String, private val cache: ResponseCache) {
    private val base = baseUrl.trimEnd('/')

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }
    /** For expense bodies: nulls matter there (clearing notes, moving an expense out of a group). */
    private val jsonWithNulls = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }

    private val client = createHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 8_000
        }
        defaultRequest { header(HttpHeaders.Authorization, "Bearer $token") }
    }

    /** Receipts (`/r/<hex>.jpg`) are public by capability URL: fetched with no auth header. */
    private val publicClient = createHttpClient {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 8_000 }
    }

    suspend fun publicBytes(path: String): ByteArray {
        val resp = try { publicClient.request(url(path)) { method = HttpMethod.Get } } catch (e: Exception) {
            throw ApiException("Can't reach the Evenly server. Check your connection.")
        }
        if (!resp.status.isSuccess()) throw ApiException("Receipt not found (${resp.status.value})", resp.status.value)
        return resp.bodyAsBytes()
    }

    fun url(path: String) = if (path.startsWith("http")) path else base + path

    private suspend fun check(resp: HttpResponse): String {
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
                ?: when (resp.status.value) {
                    401 -> "Not signed in (the app's token was rejected)"
                    404 -> "Not found"
                    else -> "Server error ${resp.status.value}"
                }
            throw ApiException(msg, resp.status.value)
        }
        return text
    }

    private suspend fun raw(method: HttpMethod, path: String, body: String?): String {
        val resp = try {
            client.request(url(path)) {
                this.method = method
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Can't reach the Evenly server. Check your connection.")
        }
        return check(resp)
    }

    /** Last good response for [path], or null. */
    fun <T> cached(path: String, ser: KSerializer<T>): T? =
        cache.get(path)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }

    /** Network GET; on success the raw response is cached under [path]. */
    suspend fun <T> get(path: String, ser: KSerializer<T>): T {
        val text = raw(HttpMethod.Get, path, null)
        val value = try {
            json.decodeFromString(ser, text)
        } catch (e: Exception) {
            throw ApiException("Unexpected response from the server (${e.message?.take(120)})")
        }
        cache.put(path, text)
        return value
    }

    suspend fun <B, T> send(method: HttpMethod, path: String, body: B?, bodySer: KSerializer<B>?, ser: KSerializer<T>, keepNulls: Boolean = false): T {
        val encoded = if (body != null && bodySer != null) (if (keepNulls) jsonWithNulls else json).encodeToString(bodySer, body) else null
        val text = raw(method, path, encoded)
        return try {
            json.decodeFromString(ser, text)
        } catch (e: Exception) {
            throw ApiException("Unexpected response from the server")
        }
    }

    suspend fun <T> sendEmpty(method: HttpMethod, path: String, ser: KSerializer<T>): T {
        val text = raw(method, path, if (method == HttpMethod.Get || method == HttpMethod.Delete) null else "{}")
        return try { json.decodeFromString(ser, text) } catch (e: Exception) { throw ApiException("Unexpected response from the server") }
    }

    suspend fun bytes(path: String): ByteArray {
        val resp = try { client.request(url(path)) { method = HttpMethod.Get } } catch (e: Exception) {
            throw ApiException("Can't reach the Evenly server. Check your connection.")
        }
        if (!resp.status.isSuccess()) check(resp)
        return resp.bodyAsBytes()
    }

    suspend fun <T> upload(path: String, fileName: String, mime: String, bytes: ByteArray, ser: KSerializer<T>): T {
        val resp = try {
            client.submitFormWithBinaryData(url(path), formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, mime)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            })
        } catch (e: Exception) {
            throw ApiException("Upload failed — can't reach the server.")
        }
        val text = check(resp)
        return try { json.decodeFromString(ser, text) } catch (e: Exception) { throw ApiException("Unexpected response from the server") }
    }
}
