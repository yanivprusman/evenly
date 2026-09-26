package com.automatelinux.evenly.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer

/**
 * One GET endpoint as UI state. Shows the cached copy immediately, then refreshes from the
 * network. A failed refresh keeps the cached data and flips [offline] on.
 */
class Resource<T>(
    private val api: Api,
    private val scope: CoroutineScope,
    val path: String,
    private val ser: KSerializer<T>,
) {
    var data by mutableStateOf<T?>(api.cached(path, ser))
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** [byUser] = pull-to-refresh: keep the indicator up at least 600ms so it reads as real. */
    fun refresh(byUser: Boolean = false) {
        scope.launch {
            val started = Clock.System.now()
            if (byUser) refreshing = true
            loading = true
            try {
                data = api.get(path, ser)
                offline = false
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
                offline = data != null
            } finally {
                if (byUser) {
                    val spent = (Clock.System.now() - started).inWholeMilliseconds
                    if (spent < 600) delay(600 - spent)
                    refreshing = false
                }
                loading = false
            }
        }
    }

    /** Locally replace the data (e.g. after a write returns the fresh object). */
    fun set(value: T) { data = value }
}

@Composable
fun <T> rememberResource(api: Api, path: String, ser: KSerializer<T>): Resource<T> {
    val scope = rememberCoroutineScope()
    val r = remember(path) { Resource(api, scope, path, ser) }
    LaunchedEffect(path) { r.refresh() }
    return r
}

/**
 * A write (POST/PATCH/DELETE) as UI state. Failures are kept with a Retry that re-runs the
 * exact same action — writes are never silently dropped.
 */
class WriteAction(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var last: (suspend () -> Unit)? = null

    fun run(block: suspend () -> Unit) {
        last = block
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    fun retry() { last?.let { run(it) } }
    fun dismiss() { error = null }
}

@Composable
fun rememberWriteAction(): WriteAction {
    val scope = rememberCoroutineScope()
    return remember { WriteAction(scope) }
}
