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

        values.clear()
        values.addAll(compute().distinctBy { it.input.lowercase() })

        valuesByKeyword.clear()
        values.forEach { value ->
            valuesByKeyword[value.input.lowercase()] = value
            valuesByKeyword[value.resolvesTo.display.lowercase()] = value

            value.aliases.forEach aliases@{ alias ->
                if (valuesByKeyword.containsKey(alias.lowercase())) return@aliases
                valuesByKeyword[alias.lowercase()] = value
            }
        }

        lastFetch = System.currentTimeMillis()
    }

    suspend fun getAll(): Iterable<ProvidedValue<T>> = mutex.withLock {
        refresh()
        return@withLock values.toSet()
    }

    suspend fun find(value: String): ProvidedValue<T>? = mutex.withLock {
        refresh()
        return valuesByKeyword[value.lowercase()]
    }

}