package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.Mappable
import dev.cowzy.cardgourmet.elrond.property.ValueProvided
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.cardgourmet.elrond.values.DynamicStringValueProvider
import kotlinx.serialization.Serializable
import java.util.*
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
        val valueTypes: List<String>,
        val operators: List<String>,
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
                val valueTypes = property.valueDefinition.supportedValueTypes.mapNotNull { type ->
                    when (type) {
                        StringValue::class -> "string"
                        NumberValue::class -> "number"
                        RegexValue::class -> "regex"
                        else -> null
                    }
                }

                val operators = property.supportedOperators.map { it.value }

                if (property is Mappable<*> && property.mappings.any()) {
                    providesValues = true
                }

                if (property is ValueProvided) {
                    providesValues = providesValues || property.valueProvider != null
                    allowsAnyValue = allowsAnyValue || (property.valueProvider == null || property.allowAnyValue)
                }

                SearchQueryProperty(property.key, valueTypes, operators)
            }

            SearchQueryFilter(filter.keywords.sorted(), properties, providesValues, !allowsAnyValue, filter.inverted)
        }.sortedBy { it.keywords.first() }
    }

    suspend fun getFilterValues(keyword: String, amount: Int, query: String?): List<String>? {
        val filter = this.filters.firstOrNull { it.keywords.contains(keyword.lowercase()) } ?: return null

        val mappables = filter.properties.filterIsInstance<Mappable<*>>()
        val valueProperties = filter.properties.filterIsInstance<ValueProvided>()
        val valueProviders = valueProperties.mapNotNull { it.valueProvider }

        val values = mutableListOf<String>()

        // First, add all mappings.
        for (mappable in mappables) {
            if (values.size >= amount) break
            val keys = mappable.mappings.keys.filter { value -> query?.let { value.toSimpleString().contains(it.toSimpleString()) } ?: true }
            values.addAll(keys)
        }

        // Now add provider values.
        for (provider in valueProviders) {
            if (values.size >= amount) break
            val remaining = amount - values.size
            when (provider) {
                is DynamicStringValueProvider -> values.addAll(provider.getValues(remaining, query))
                else -> values.addAll(provider.getValues().filter { value -> query?.let { value.toSimpleString().contains(it.toSimpleString()) } ?: true })
            }
        }

        return values.sorted().take(amount)
    }

//    fun toPaginationValueQueryBuilder(
//        expression: QueryExpressionBuilderResult,
//        flags: Set<T>,
//        distinctBy: KProperty1<*, *>,
//        id: UUID,
//        sortColumns: List<ElrondSortColumn> = emptyList(),
//        preferredLanguage: String,
//        attempt: Int = 0,
//        applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null
//    ): SelectQueryBuilder? {
//        var searchQuery = SearchQuery(
//            expression = expression.expression,
//            distinctBy = distinctBy,
//            sortColumns = sortColumns,
//            flags = flags,
//            preferredLanguage = preferredLanguage,
//        )
//
//        val attemptTransformer = when (attempt) {
//            0 -> null
//            else -> attemptTransformers.getOrNull(attempt - 1) ?: return null
//        }
//
//        searchQuery = attemptTransformer?.invoke(searchQuery) ?: searchQuery
//
//        return searchQuery.toPaginationValueQueryBuilder(
//            config = config,
//            distinctBy = distinctBy,
//            id = id,
//            sortColumns = sortColumns,
//            sqlBuilder = SearchQuerySqlBuilder(
//                affectedTables = customTables,
//                apply = customBuilder
//            ),
//            applyCustomConditions = applyCustomConditions,
//        )
//    }
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
