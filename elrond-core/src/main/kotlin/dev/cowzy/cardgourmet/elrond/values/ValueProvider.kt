package dev.cowzy.cardgourmet.elrond.values

interface ValueProvider<T> {

    suspend fun getValues(): Set<T>

}

interface DynamicStringValueProvider : ValueProvider<String> {

    suspend fun getValues(limit: Int, filter: String? = null): List<String>

    override suspend fun getValues(): Set<String> = getValues(50, null).toSet()

}
