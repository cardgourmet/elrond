package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.cardgourmet.elrond.values.ProvidedValue
import dev.cowzy.kuery.query.SelectQueryBuilder
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

open class SearchQueryExecutor<SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>>(
    val config: SearchQuerySqlConfig,
    val flags: Set<SearchFlag>,
    val sortModes: List<SortMode>,
    val distinctModes: Map<DistinctMode, KProperty1<*, UUID>>,
    val fallbackDistinctMode: DistinctMode,
    val fallbackSortMode: (QueryExpression) -> SortMode,
    val filters: List<QueryFilter>,
    var fallbackFilter: QueryFilter?,
    val attemptTransformers: List<SearchQueryTransformer<SearchFlag, DistinctMode>>,
    val customTables: ((SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode) -> Set<KClass<*>>)?,
    val customBuilder: ((SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit)?
) {
    @Serializable
    data class SearchQueryFilter(
        val keywords: List<String>,
        val properties: List<SearchQueryProperty>,
        val providesValues: Boolean,
        val strictValues: Boolean,
        val inverted: Boolean
    )

    @Serializable
    data class SearchQueryProperty(
        val key: String,
        val valueTypes: List<ValueType>,
        val operators: List<String>,
    )

    @Serializable
    data class ValueType(
        val type: String,
        val format: String?
    )

    @Serializable
    data class FilterValues(
        val total: Int,
        val matches: Int,
        val values: List<FilterValue>
    )

    @Serializable
    data class FilterValue(
        val value: String,
        val type: String,
        val aliases: List<String>?,
        val resolvesTo: String?,
        val resolvesToOperator: SearchQueryOperator?,
        val language: String?,
    )

    fun describeSearchFilters(query: String?): List<SearchQueryFilter> {
        val filters = this.filters.filter { filter ->
            query?.let { query ->
                filter.keywords.any { it.toSimpleString().contains(query.toSimpleString()) }
            } ?: true
        }

        return filters.map { filter ->
            var allowsAnyValue = false
            var providesValues = false

            val properties = filter.properties.map { property ->
                val valueDefinitions = property.valueDefinition.supportedValueTypes.associateWith { property.valueDefinition.getDefinition(it) }

                val valueTypes = valueDefinitions.mapNotNull { (type, definition) ->
                    val valueType = when (type) {
                        StringValue::class -> "string"
                        NumberValue::class -> "number"
                        RegexValue::class -> "regex"
                        else -> return@mapNotNull null
                    }

                    ValueType(valueType, definition.format)
                }.toMutableSet()

                val operators = property.supportedOperators.map { it.value }

                val provider = property.valueDefinition.provider
                if (provider != null) {
                    allowsAnyValue = allowsAnyValue || !provider.strictValues
                    providesValues = true
                    valueTypes.add(ValueType("string", null))
                } else {
                    allowsAnyValue = true
                }

                SearchQueryProperty(property.key, valueTypes.sortedBy { it.type }, operators)
            }

            SearchQueryFilter(filter.keywords.sorted(), properties, providesValues, !allowsAnyValue, filter.inverted)
        }.sortedBy { it.keywords.first() }
    }

    suspend fun getFilterValues(
        keyword: String,
        amount: Int,
        query: String?,
        preferredLanguage: String? = null,
        type: String? = null
    ): FilterValues? {
        val filter = this.filters.firstOrNull { it.keywords.contains(keyword.lowercase()) } ?: return null

        val providers = filter.properties.mapNotNull { it.valueDefinition.provider }

        val providedValues = mutableListOf<ProvidedValue<*>>()
        val totalCount: Int
        val matchCount: Int

        if (query != null) {
            val usedInputs = mutableSetOf<String>()

            // First find any exact matches
            val exactMatches = providers
                .mapNotNull { it.findValue(query) }
                .filter { type == null || it.type == type }
                .filter { usedInputs.add(it.input) }
                .sortedBy { it.input }

            providedValues.addAll(exactMatches.take(amount))

            // Next find any values that contain the keyword
            val fuzzyMatches = providers
                .map { it.getValues(query, preferredLanguage) }
                .flatten()
                .filter { type == null || it.type == type }
                .filter { usedInputs.add(it.input) }
                .sortedBy { it.input } - exactMatches.toSet()

            providedValues.addAll(fuzzyMatches.take(amount - providedValues.size))

            // If there are no fuzzy matches, search again without the language
            if (fuzzyMatches.isEmpty() && preferredLanguage != null) {
                val languageMatches = providers
                    .map { it.getValues(query, null) }
                    .flatten()
                    .filter { type == null || it.type == type }
                    .filter { usedInputs.add(it.input) }
                    .sortedBy { it.input } - exactMatches.toSet()

                providedValues.addAll(languageMatches.take(amount - providedValues.size))
            }

            matchCount = providedValues.size + fuzzyMatches.size
            totalCount = providers.sumOf { it.getValues().count() }
        } else {
            var values = providers
                .map { it.getValues(preferredLanguage) }
                .flatten()
                .filter { type == null || it.type == type }
                .distinctBy { it.input }
                .sortedBy { it.input }

            // If there are no values, search again without the language
            if (values.isEmpty() && preferredLanguage != null) {
                values = providers
                    .map { it.getValues(null) }
                    .flatten()
                    .filter { type == null || it.type == type }
                    .distinctBy { it.input }
                    .sortedBy { it.input }
            }

            providedValues.addAll(values.take(amount))

            matchCount = values.size
            totalCount = providers.sumOf { it.getValues().count() }
        }

        return FilterValues(
            totalCount,
            matchCount,
            providedValues.map { value ->
                FilterValue(
                    value.input,
                    value.type,
                    value.aliases.sorted().takeIf { it.isNotEmpty() },
                    value.resolvesTo.display.takeIf { !value.resolvesTo.display.equals(value.input, ignoreCase = true) },
                    value.resolvesTo.operator,
                    value.language
                )
            }
        )
    }
}

typealias SearchQueryTransformer<F, D> = (SearchQuery<F, D>) -> SearchQuery<F, D>?

class SearchQueryExecutorBuilder<SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>>(
    private val config: SearchQuerySqlConfig
) {

    private val flags = mutableSetOf<SearchFlag>()
    private val sortModes = mutableListOf<SortMode>()
    private var fallbackSortMode: (QueryExpression) -> SortMode = { sortModes.first() }
    private var fallbackDistinctMode: DistinctMode? = null
    private val distinctModes = mutableMapOf<DistinctMode, KProperty1<*, UUID>>()
    private val filters = mutableListOf<QueryFilter>()
    private var fallbackFilter: QueryFilter? = null
    private val attemptTransformers = mutableListOf<SearchQueryTransformer<SearchFlag, DistinctMode>>()
    private var customTables: ((SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode) -> Set<KClass<*>>)? = null
    private var customBuilder: ((SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit)? = null

    fun flags(vararg flags: SearchFlag) = this.apply { this.flags.addAll(flags) }

    fun sortModes(vararg sortModes: SortMode, fallback: (QueryExpression) -> SortMode) = this.apply {
        this.sortModes.addAll(sortModes)
        this.fallbackSortMode = fallback
    }

    fun filters(filters: List<QueryFilter>) = this.apply { this.filters.addAll(filters) }

    fun fallbackFilter(filter: QueryFilter?) = this.apply { this.fallbackFilter = filter }

    fun transformAttempt(transform: SearchQueryTransformer<SearchFlag, DistinctMode>) = this.apply { this.attemptTransformers.add(transform) }

    fun customTables(builder: (SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode) -> Set<KClass<*>>) = this.apply { this.customTables = builder }

    fun customBuilder(builder: (SearchQuery<SearchFlag, DistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = this.apply { this.customBuilder = builder }

    fun fallbackDistinctMode(distinctMode: DistinctMode) = this.apply { this.fallbackDistinctMode = distinctMode }

    fun distinctMode(distinctMode: DistinctMode, property: KProperty1<*, UUID>) = this.apply {
        if (fallbackDistinctMode == null) fallbackDistinctMode = distinctMode
        this.distinctModes[distinctMode] = property
    }

    fun build() = SearchQueryExecutor(
        config = config,
        flags = flags,
        sortModes = sortModes,
        fallbackSortMode = fallbackSortMode,
        filters = filters,
        fallbackFilter = fallbackFilter,
        attemptTransformers = attemptTransformers,
        customTables = customTables,
        customBuilder = customBuilder,
        distinctModes = distinctModes,
        fallbackDistinctMode = distinctModes.keys.first()
    )
}
