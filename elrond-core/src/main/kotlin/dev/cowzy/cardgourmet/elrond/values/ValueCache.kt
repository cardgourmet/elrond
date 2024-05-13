package dev.cowzy.cardgourmet.elrond.values

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ValueCache<T : Any>(
    private val ttl: Long = 3600,
    private val compute: suspend () -> Iterable<ProvidedValue<T>>,
) {

    private val mutex = Mutex()

    private val values = mutableListOf<ProvidedValue<T>>()
    private val valuesByKeyword = mutableMapOf<String, ProvidedValue<T>>()

    private val isDirty get() = ttl < 0 || (System.currentTimeMillis() - lastFetch) > (ttl * 1000)
    private var lastFetch: Long = 0

    private suspend fun refresh() {
        if (!isDirty) return

        this.values.clear()
        this.valuesByKeyword.clear()

        this.compute().distinctBy {
            it.input.lowercase()
        }.forEach { value ->
            val isInputUsed = this.valuesByKeyword.containsKey(value.input.lowercase())
            val availableAliases = value.aliases.filter { alias -> !this.valuesByKeyword.containsKey(alias.lowercase()) }

            if (!isInputUsed) {
                values.add(value)
            } else {
                if (availableAliases.isEmpty()) return@forEach
                values.add(value.copy(input = availableAliases.first()))
            }

            this.valuesByKeyword[value.input.lowercase()] = value
            this.valuesByKeyword[value.resolvesTo.display.lowercase()] = value

            value.aliases.forEach aliases@{ alias ->
                if (this.valuesByKeyword.containsKey(alias.lowercase())) return@aliases
                this.valuesByKeyword[alias.lowercase()] = value
            }
        }

        this.lastFetch = System.currentTimeMillis()
    }

    suspend fun getAll(): Iterable<ProvidedValue<T>> = mutex.withLock {
        refresh()
        return@withLock values.toSet()
    }

    suspend fun find(value: String): ProvidedValue<T>? {
        refresh()
        return valuesByKeyword[value.lowercase()]
    }

}