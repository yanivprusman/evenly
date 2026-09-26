package com.automatelinux.evenly.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.automatelinux.evenly.data.model.Currency
import com.automatelinux.evenly.data.model.User
import com.automatelinux.evenly.nav.Navigator
import com.automatelinux.evenly.util.CurrencyTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** App-wide state: who I am, the people I've seen, currencies, navigation. */
class AppContext(
    val api: Api,
    val platform: Platform,
    val nav: Navigator,
    private val scope: CoroutineScope,
) {
    var me by mutableStateOf<User?>(api.cached("/api/me", User.serializer()))
    var meError by mutableStateOf<String?>(null)
    var currencies by mutableStateOf(api.cached("/api/currencies", ListSerializer(Currency.serializer())) ?: DEFAULT_CURRENCIES)
        private set
    private val people = mutableStateMapOf<Int, User>()

    init {
        currencies.forEach { CurrencyTable.learn(it.code, it.symbol) }
        me?.let { people[it.id] = it }
    }

    val meId: Int get() = me?.id ?: -1
    val defaultCurrency: String get() = me?.defaultCurrency ?: "ILS"

    fun load() {
        scope.launch {
            try {
                val m = api.get("/api/me", User.serializer())
                me = m
                people[m.id] = m
                meError = null
            } catch (e: Exception) {
                meError = e.message
            }
        }
        scope.launch {
            runCatching { api.get("/api/currencies", ListSerializer(Currency.serializer())) }.getOrNull()?.let { list ->
                if (list.isNotEmpty()) {
                    currencies = list
                    list.forEach { CurrencyTable.learn(it.code, it.symbol) }
                }
            }
        }
    }

    fun learn(users: Iterable<User>) = users.forEach { people[it.id] = it }
    fun learn(user: User) { people[user.id] = user }
    fun user(id: Int): User? = people[id]

    /** "You" for me, first name otherwise. */
    fun shortName(id: Int): String = if (id == meId) "You" else people[id]?.name?.substringBefore(' ') ?: "Someone"
    fun fullName(id: Int): String = if (id == meId) "You" else people[id]?.name ?: "Someone"

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    companion object {
        val DEFAULT_CURRENCIES = listOf(
            Currency("ILS", "₪", "Israeli new shekel"),
            Currency("USD", "$", "US dollar"),
            Currency("EUR", "€", "Euro"),
            Currency("GBP", "£", "British pound"),
        )
    }
}

val LocalApp = staticCompositionLocalOf<AppContext> { error("AppContext not provided") }
