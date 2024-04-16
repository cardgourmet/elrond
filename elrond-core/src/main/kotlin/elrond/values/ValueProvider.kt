package elrond.values

interface ValueProvider<T> {
    suspend fun getValues(): Set<T>
}
