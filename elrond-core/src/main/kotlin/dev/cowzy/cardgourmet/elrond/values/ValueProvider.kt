package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

interface ValueProvider<T> {

    suspend fun getValues(): Iterable<T>

}

interface DynamicStringValueProvider : ValueProvider<String> {

    suspend fun getValues(limit: Int, filter: String? = null): List<String>

    override suspend fun getValues(): Iterable<String> = getValues(50, null).toSet()

}

typealias MappingProvider<Value, Output> = ValueProvider<Pair<Value, Pair<Output, SearchQueryOperator?>>>
