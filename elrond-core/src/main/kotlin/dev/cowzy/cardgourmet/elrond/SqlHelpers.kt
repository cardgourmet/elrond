package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.api.model.Cursor
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.kuery.ColumnIndex
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.column.transformer.UuidColumnTransformer
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.placeholder
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

fun createSqlAlias(length: Int = 8): String {
    val allowedChars = ('a'..'z')
    return (1..length).map { allowedChars.random() }.joinToString("")
}

data class ElrondPaginationValues(val values: List<ElrondPaginationValue> = emptyList(), val id: UUID? = null)

typealias ElrondPaginationValue = Any?

data class SearchQueryResult<T>(
    val data: List<Entry<T>>,
    val attempt: Int,
    val finalSearchQuery: SearchQuery<*>,
    val expressionResult: QueryExpressionBuilderResult
) {
    data class Entry<T>(
        val value: T,
        val cursor: UUID,
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
    pagination: Cursor,
    preferredLanguage: String,
    mapResults: (ResultSet, (ResultSet, ColumnIndex) -> UUID) -> List<SearchQueryResult.Entry<T>>,
    overrideFlags: Set<E>? = null,
    connection: Connection
): SearchQueryResult<T> {
    var lastSearchQuery: SearchQueryExecutor.Result<E>? = null

    val (expressionResult, flags) = parse(query)

    val innerSortColumns = if (pagination.direction == Order.ASCENDING) sortColumns else sortColumns.map { it.flipped() }

    var attempt = 0
    while (true) {
        val distinctBySortColumn = ElrondSortColumn(distinctBy, pagination.direction).apply { sortName = "distinct_id" }
        val pageColumns = sortColumns + distinctBySortColumn

        val paginationValues = pagination.lastId?.let { id ->
            val paginationBuilder = this.toQueryBuilder(
                expression = expressionResult,
                flags = overrideFlags ?: flags,
                distinctBy = distinctBy,
                preferredLanguage = preferredLanguage,
                attempt = attempt,
                applyCustomConditions = {
                    it.where(distinctBy, id)
                    applyCustomConditions?.invoke(it)
                }
            )?.builder?.apply {
                this.clearSelect()
                innerSortColumns.forEach { this.select(it.property) }
            }

            paginationBuilder?.findFirst(connection) { row, index ->
                val values = innerSortColumns.map { row.getObject(index.getAndIncrement()) }
                ElrondPaginationValues(values, id)
            }
        } ?: ElrondPaginationValues()

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

        if (paginationValues.values.any()) {
            (innerSortColumns + distinctBySortColumn).apply(
                innerBuilder,
                values = paginationValues.values + paginationValues.id,
                distinctBySortColumn = distinctBySortColumn,
                inverse = false
            )
        }

        innerSortColumns.forEach { innerBuilder.orderBy("innerQuery.${it.sortName}", it.order) }
        innerBuilder.orderBy("innerQuery.${distinctBySortColumn.sortName}", pagination.direction)

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
        val results = mapResults(resultSet) { row, index -> UuidColumnTransformer.fromSql(row, index)!! }

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

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.getPageItems(
    query: String,
    distinctBy: KProperty1<*, *>,
    sortColumns: List<ElrondSortColumn>,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    pageSize: Int,
    preferredLanguage: String,
    overrideFlags: Set<T>? = null,
    connection: Connection
): List<UUID?> {
    val (expressionResult, flags) = parse(query)

    var attempt = 0
    while (true) {
        val count = countAttempt(
            query = query,
            distinctBy = distinctBy,
            preferredLanguage = preferredLanguage,
            applyCustomConditions = applyCustomConditions,
            connection = connection,
            overrideFlags = overrideFlags,
            attempt = attempt
        )?.count ?: 0

        if (count in 1..pageSize) return listOf(null)

        val distinctBySortColumn = ElrondSortColumn(distinctBy, Order.ASCENDING).apply { sortName = "distinct_id" }
        val pageColumns = sortColumns + distinctBySortColumn

        val result = this.toQueryBuilder(
            expression = expressionResult,
            flags = overrideFlags ?: flags,
            distinctBy = distinctBy,
            preferredLanguage = preferredLanguage,
            sortColumns = sortColumns,
            attempt = attempt,
            applyCustomConditions = applyCustomConditions
        ) ?: break

        val innerBuilder = QueryBuilder.Companion
            .selectBuilder(result.builder, "innerQuery")
            .selectRaw("*")
            .select("ROW_NUMBER() OVER (ORDER BY ${pageColumns.joinToString { 
                "innerQuery.${it.sortName} ${it.order.value}"
            }}) as row_number")

        val builder = QueryBuilder.Companion
            .selectBuilder(innerBuilder, "query")
            .whereRaw("row_number % $pageSize = 0")
            .orderByRaw("row_number")

        pageColumns.forEach {
            val key = it.sortName

            if (distinctBySortColumn == it) {
                builder.selectRaw("query.$key")
                builder.orderBy("query.$key", Order.ASCENDING)
            } else {
                builder.orderBy("query.$key", it.order)
            }
        }

        val ids = builder.get(connection) { row, index ->
            UuidColumnTransformer.fromSql(row, index)!!
        }

        if (count > 0 || ids.any()) return listOf(null) + ids
        attempt += 1
    }

    return listOf(null)
}

private fun List<ElrondSortColumn>.apply(builder: WhereQueryBuilder<*>, index: Int = 0, values: List<Any?>, distinctBySortColumn: ElrondSortColumn, inverse: Boolean = false) {
    val current = this[index]
    val order = current.order

    var operator = if ((order == Order.ASCENDING && !inverse) || (order == Order.DESCENDING && inverse)) ">" else "<"
    if (current.flipped) {
        operator += "="
    }

    val column = current.property
    val columnName = "innerQuery.${current.sortName}"

    val value = values[index]
    val type = column.returnType.classifier as KClass<*>
    val mappedValue = value ?: 2147483647.takeIf { type.isSubclassOf(Number::class) }

    if (index >= this.size - 1) {
        builder.where(columnName, operator, value)
        return
    }

    builder.where {
        if (mappedValue != null) {
            it.where(columnName, operator, mappedValue, column.placeholder())
        }

        it.orWhere { inner ->
            if (mappedValue == null) {
                inner.whereRaw("$columnName IS NULL")
            } else {
                inner.where(columnName, mappedValue, column.placeholder())
            }

            this.apply(inner, index + 1, values, distinctBySortColumn, inverse)
        }
    }
}
