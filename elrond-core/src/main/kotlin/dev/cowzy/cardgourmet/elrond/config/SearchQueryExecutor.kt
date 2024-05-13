package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.query.QueryExpressionBuilderResult
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQuerySqlBuilder
import dev.cowzy.cardgourmet.elrond.query.toQueryBuilder
import dev.cowzy.cardgourmet.elrond.values.ProvidedValue
import dev.cowzy.kuery.query.SelectQueryBuilder
import kotlinx.serialization.Serializable
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

    suspend fun getFilterValues(keyword: String, amount: Int, query: String?): FilterValues? {
        val filter = this.filters.firstOrNull { it.keywords.contains(keyword.lowercase()) } ?: return null

        val providers = filter.properties.mapNotNull { it.valueDefinition.provider }

        val providedValues = mutableListOf<ProvidedValue<*>>()
        val totalCount = providers.sumOf { it.getValues().count() }
        val matchCount: Int

        if (query != null) {
            // First find any exact matches
            val exactMatches = providers.mapNotNull { it.findValue(query) }
            providedValues.addAll(exactMatches.take(amount))

            // Next find any values that contain the keyword
            val fuzzyMatches = providers.map { it.getValues(query) }.flatten() - exactMatches.toSet()
            providedValues.addAll(fuzzyMatches.take(amount - providedValues.size))

            matchCount = providedValues.size + fuzzyMatches.size
        } else {
            val values = providers.map { it.getValues() }.flatten()
            providedValues.addAll(values.take(amount))
            matchCount = values.size
        }

        return FilterValues(
            totalCount,
            matchCount,
            providedValues.map { value ->
                FilterValue(
                    value.input,
                    value.type,
                    value.aliases.sorted().takeIf { it.isNotEmpty() },
                    value.resolvesTo.display.takeIf { value.resolvesTo.display != value.input },
                    value.resolvesTo.operator
                )
            }
        )
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
