package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.QueryExpressionBuilderResult
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.parseQueryExpression
import dev.cowzy.cardgourmet.elrond.query.stripFlags
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.QueryBuilder
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.innerJoin
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.reflect.KProperty1

fun createSqlAlias(length: Int = 8): String {
    val allowedChars = ('a'..'z')
    return (1..length).map { allowedChars.random() }.joinToString("")
}

data class SearchQueryResult<T>(
    val data: List<Entry<T>>,
    val attempt: Int,
    val finalSearchQuery: SearchQuery<*>,
    val expressionResult: QueryExpressionBuilderResult
) {
    data class Entry<T>(
        val value: T,
        val preferredLanguage: String? = null,
        val preferredPrintId: UUID? = null,
        val preferredFaceIndex: Int? = null,
    )
}

suspend fun <E : Enum<E>> SearchQueryExecutor<E>.parse(query: String): Pair<QueryExpressionBuilderResult, Set<E>> {
    val (strippedQuery, flags) = query.stripFlags(flags)
    return strippedQuery.parseQueryExpression(filters, fallbackFilter) to flags
}

suspend fun <T, E : Enum<E>> SearchQueryExecutor<E>.searchPrints(
    query: String,
    distinctBy: KProperty1<*, *>,
    sortColumns: List<ElrondSortColumn>,
    builder: SelectQueryBuilder,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    limit: Int,
    offset: Int,
    preferredLanguage: String,
    mapResults: (ResultSet) -> List<SearchQueryResult.Entry<T>>,
    overrideFlags: Set<E>? = null,
    connection: Connection
): SearchQueryResult<T> {
    var lastSearchQuery: SearchQueryExecutor.Result<E>? = null

    val (expressionResult, flags) = parse(query)

    var attempt = 0
    while (true) {
        val distinctBySortColumn = ElrondSortColumn(distinctBy, Order.ASCENDING).apply { sortName = "distinct_id" }
        val pageColumns = sortColumns + distinctBySortColumn

        val result = this.toQueryBuilder(
            expression = expressionResult,
            flags = overrideFlags ?: flags,
            distinctBy = distinctBy,
            sortColumns = sortColumns,
            preferredLanguage = preferredLanguage,
            attempt = attempt,
            applyCustomConditions = applyCustomConditions
        ) ?: break

        lastSearchQuery = result

        val innerBuilder = QueryBuilder.Companion
            .selectBuilder(result.builder, "innerQuery")
            .limit(limit)
            .offset(offset)

        sortColumns.forEach { innerBuilder.orderBy("innerQuery.${it.sortName}", it.order) }
        innerBuilder.orderBy("innerQuery.${distinctBySortColumn.sortName}", Order.ASCENDING)

        val outerBuilder = builder
            .clone()
            .innerJoin(innerBuilder, "query") {
                it.whereColumn(distinctBy, "query.distinct_id")
            }

        pageColumns.forEach {
            val key = it.sortName
            if (distinctBySortColumn == it) {
                outerBuilder.selectRaw("query.$key")
                outerBuilder.orderBy("query.$key", Order.ASCENDING)
            } else {
                outerBuilder.orderBy("query.$key", it.order)
            }
        }

        val resultSet = outerBuilder.getRaw(connection)
        val results = mapResults(resultSet)

        if (results.any()) return SearchQueryResult(results, attempt, result.executedQuery, result.expressionResult)
        attempt += 1
    }

    return SearchQueryResult(emptyList(), attempt, lastSearchQuery!!.executedQuery, lastSearchQuery.expressionResult)
}

data class SearchQueryCountResult(
    val count: Int,
    val attempt: Int,
    val finalSearchQuery: SearchQuery<*>,
    val expressionResult: QueryExpressionBuilderResult
)

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.count(
    query: String,
    distinctBy: KProperty1<*, *>,
    preferredLanguage: String,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    overrideFlags: Set<T>? = null,
    connection: Connection,
): SearchQueryCountResult {
    var lastQueryResult: SearchQueryCountResult? = null

    var attempt = 0
    while (true) {
        val result = this.countAttempt(
            query = query,
            distinctBy = distinctBy,
            preferredLanguage = preferredLanguage,
            applyCustomConditions = applyCustomConditions,
            connection = connection,
            overrideFlags = overrideFlags,
            attempt = attempt
        ) ?: break

        lastQueryResult = result

        if (result.count > 0) return result

        attempt += 1
    }

    return lastQueryResult!!
}

private suspend fun <T : Enum<T>> SearchQueryExecutor<T>.countAttempt(
    query: String,
    distinctBy: KProperty1<*, *>,
    preferredLanguage: String,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    overrideFlags: Set<T>? = null,
    connection: Connection,
    attempt: Int
): SearchQueryCountResult? {
    val (expressionResult, flags) = parse(query)

    val result = this.toQueryBuilder(
        expression = expressionResult,
        flags = overrideFlags ?: flags,
        distinctBy = distinctBy,
        preferredLanguage = preferredLanguage,
        attempt = attempt,
        applyCustomConditions = applyCustomConditions
    ) ?: return null

    val count = QueryBuilder.selectBuilder(result.builder, "innerQuery")
        .selectCount()
        .single(connection) { row, index -> row.getInt(index.getAndIncrement()) }

    return SearchQueryCountResult(count, attempt, result.executedQuery, result.expressionResult)
}