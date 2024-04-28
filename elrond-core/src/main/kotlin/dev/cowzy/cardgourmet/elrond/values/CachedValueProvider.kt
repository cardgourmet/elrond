package dev.cowzy.cardgourmet.elrond.values

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO: clear cached if unused for a long time
abstract class CachedValueProvider<T>(private val ttl: Long = 3600) : ValueProvider<T> {

    private val mutex = Mutex()
    private val cache = mutableSetOf<T>()

    private val isDirty get() = (System.currentTimeMillis() - lastFetch) > (ttl * 1000)
    private var lastFetch: Long = 0

    override suspend fun getValues(): Iterable<T> = mutex.withLock {
        if (isDirty) {
            cache.clear()
            cache.addAll(fetchValues())
            lastFetch = System.currentTimeMillis()
        }

        return@withLock cache.toSet()
    }

    protected abstract suspend fun fetchValues(): Iterable<T>

}