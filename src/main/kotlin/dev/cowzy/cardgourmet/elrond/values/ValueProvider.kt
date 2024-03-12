package dev.cowzy.cardgourmet.elrond.values

interface ValueProvider<T> {
    suspend fun getValues(): Set<T>
}
