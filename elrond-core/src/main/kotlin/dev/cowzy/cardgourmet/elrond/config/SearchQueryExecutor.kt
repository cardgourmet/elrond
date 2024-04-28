package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.cardgourmet.elrond.values.DynamicStringValueProvider
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class SearchQueryExecutor<T : Enum<T>>(
    val config: SearchQueryConfig,
    val flags: Set<T>,
    val filters: List<QueryFilter>,
    var fallbackFilter: QueryFilter?,
    val attemptTransformers: List<SearchQueryTransformer<T>>,
    val customTables: ((SearchQuery<T>) -> Set<KClass<*>>)?,
    val customBuilder: ((SearchQuery<T>, SelectQueryBuilder) -> Unit)?
) {
    data class Result<T : Enum<T>>(
        val builder: SelectQueryBuilder,
        val executedQuery: SearchQuery<T>,
        val expressionResult: QueryExpressionBuilderResult
    )

    suspend fun toQueryBuilder(
        expression: QueryExpressionBuilderResult,
        flags: Set<T>,
        distinctBy: KProperty1<*, *>,
        sortColumns: List<ElrondSortColumn> = emptyList(),
        preferredLanguage: String,
        attempt: Int = 0,
        applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null
    ): Result<T>? {
        var searchQuery = SearchQuery(
            expression = expression.expression,
            distinctBy = distinctBy,
            sortColumns = sortColumns,
            flags = flags,
            preferredLanguage = preferredLanguage,
        )

        val attemptTransformer = when (attempt) {
            0 -> null
            else -> attemptTransformers.getOrNull(attempt - 1) ?: return null
        }

        searchQuery = attemptTransformer?.invoke(searchQuery) ?: searchQuery

        return Result(
            executedQuery = searchQuery,
            builder = searchQuery.toQueryBuilder(
                config = config,
                distinctBy = distinctBy,
                sortColumns = sortColumns,
                sqlBuilder = SearchQuerySqlBuilder(
                    affectedTables = customTables,
                    apply = customBuilder
                ),
                applyCustomConditions = applyCustomConditions,
            ),
            expressionResult = expression
        )
    }

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
        val mappings: List<Pair<String, String>>,
        val values: List<String>,
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
                }

                val operators = property.supportedOperators.map { it.value }

                valueDefinitions.values.forEach { definition ->
                    val mappings = definition.mappingsProvider
                    if (mappings != null) {
                        providesValues = true
                    }

                    val valueProvider = definition.valueProvider
                    if (valueProvider != null) {
                        providesValues = true
                        allowsAnyValue = allowsAnyValue || !definition.useStrictValues
                    } else {
                        allowsAnyValue = true
                    }
                }

                SearchQueryProperty(property.key, valueTypes, operators)
            }

            SearchQueryFilter(filter.keywords.sorted(), properties, providesValues, !allowsAnyValue, filter.inverted)
        }.sortedBy { it.keywords.first() }
    }

    suspend fun getFilterValues(keyword: String, amount: Int, query: String?): FilterValues? {
        val filter = this.filters.firstOrNull { it.keywords.contains(keyword.lowercase()) } ?: return null

        val valueDefinitions = filter.properties.map { property ->
            property.valueDefinition.supportedValueTypes.map { type ->
                property.valueDefinition.getDefinition(type)
            }
        }.flatten()

        val mappings = mutableSetOf<Pair<String, String>>()
        val values = mutableSetOf<String>()

        val mappingProviders = valueDefinitions.mapNotNull { it.mappingsProvider }
        for (provider in mappingProviders) {
            if (mappings.size >= amount) break
            mappings.addAll(provider.getMappingValues(query))
        }

        val valueProviders = valueDefinitions.mapNotNull { it.valueProvider }
        for (provider in valueProviders) {
            val remaining = amount - values.size
            if (remaining <= 0) break
            values.addAll(provider.getValues(remaining, query))
        }

        mappings.removeIf { values.contains(it.first) }

        val takeMappings = minOf(amount, mappings.size)
        val takeValues = minOf(amount - takeMappings, values.size)

        return FilterValues(mappings.sortedBy { it.first }.take(takeMappings), values.sorted().take(takeValues))
    }

    private suspend fun <T : Pair<*, *>> ValueProvider<T>.getMappingValues(query: String?): Iterable<Pair<String, String>> {
        val simpleQuery = query?.toSimpleString()
        return this.getValues().filter { value ->
            simpleQuery?.let { value.first.toString().toSimpleString().contains(it) } ?: true
        }.map {
            val value = it.second
            it.first.toString() to when (value) {
                is LocalDate -> value.format(DateTimeFormatter.ISO_DATE)
                else -> value.toString()
            }
        }
    }

    private suspend fun <T> ValueProvider<T>.getValues(limit: Int, query: String?): Iterable<String> {
        val simpleQuery = query?.toSimpleString()
        return when (this) {
            is DynamicStringValueProvider -> this.getValues(limit, query)
            else -> this.getValues().filter { value -> simpleQuery?.let { value.toString().toSimpleString().contains(it) } ?: true }.map { it.toString() }
        }
    }
}

typealias SearchQueryTransformer<T> = (SearchQuery<T>) -> SearchQuery<T>?

class SearchQueryExecutorBuilder<T : Enum<T>>(
    private val config: SearchQueryConfig
) {

    private val flags = mutableSetOf<T>()
    private val filters = mutableListOf<QueryFilter>()
    private var fallbackFilter: QueryFilter? = null
    private val attemptTransformers = mutableListOf<SearchQueryTransformer<T>>()
    private var customTables: ((SearchQuery<T>) -> Set<KClass<*>>)? = null
    private var customBuilder: ((SearchQuery<T>, SelectQueryBuilder) -> Unit)? = null

    fun flags(vararg flags: T) = this.apply { this.flags.addAll(flags) }

    fun filters(filters: List<QueryFilter>) = this.apply { this.filters.addAll(filters) }

    fun fallbackFilter(filter: QueryFilter?) = this.apply { this.fallbackFilter = filter }

    fun transformAttempt(transform: SearchQueryTransformer<T>) = this.apply { this.attemptTransformers.add(transform) }

    fun customTables(builder: (SearchQuery<T>) -> Set<KClass<*>>) = this.apply { this.customTables = builder }

    fun customBuilder(builder: (SearchQuery<T>, SelectQueryBuilder) -> Unit) = this.apply { this.customBuilder = builder }

    fun clone() = SearchQueryExecutorBuilder<T>(config).apply {
        this.flags.addAll(this@SearchQueryExecutorBuilder.flags)
        this.filters.addAll(this@SearchQueryExecutorBuilder.filters)
        this.fallbackFilter = this@SearchQueryExecutorBuilder.fallbackFilter
        this.attemptTransformers.addAll(this@SearchQueryExecutorBuilder.attemptTransformers)
        this.customTables = this@SearchQueryExecutorBuilder.customTables
        this.customBuilder = this@SearchQueryExecutorBuilder.customBuilder
    }

    fun build() = SearchQueryExecutor(
        config = config,
        flags = flags,
        filters = filters,
        fallbackFilter = fallbackFilter,
        attemptTransformers = attemptTransformers,
        customTables = customTables,
        customBuilder = customBuilder
    )
}
