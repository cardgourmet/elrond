package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

class AutoMappingProvider(
    private val provider: ValueProvider<String>,
    private val customMappings: Map<String, String>? = null
) : MappingProvider<String, String> {

    override suspend fun getValues(): Iterable<Pair<String, Pair<String, SearchQueryOperator?>>> {
        val mappings = customMappings?.entries?.map { (key, value) -> key to (value to null) }?.toMutableList() ?: mutableListOf()
        val values = provider.getValues().filter { it.contains("_") }
        mappings.addAll(values.map { it.replace("_", "") to (it to null) })
        return mappings.distinctBy { it.first.lowercase() }
    }

}