package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.query.*
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
        sortColumns: List<dev.cowzy.cardgourmet.elrond.ElrondSortColumn> = emptyList(),
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

    fun toPaginationValueQueryBuilder(
        expression: QueryExpressionBuilderResult,
        flags: Set<T>,
        distinctBy: KProperty1<*, *>,
        id: UUID,
        sortColumns: List<dev.cowzy.cardgourmet.elrond.ElrondSortColumn> = emptyList(),
        preferredLanguage: String,
        attempt: Int = 0,
        applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null
    ): SelectQueryBuilder? {
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

        return searchQuery.toPaginationValueQueryBuilder(
            config = config,
            distinctBy = distinctBy,
            id = id,
            sortColumns = sortColumns,
            sqlBuilder = SearchQuerySqlBuilder(
                affectedTables = customTables,
                apply = customBuilder
            ),
            applyCustomConditions = applyCustomConditions,
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
