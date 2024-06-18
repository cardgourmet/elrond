package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.elrond.BadDistinctModeException
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.kuery.ColumnIndex
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.parse
import dev.cowzy.kuery.reflection.simpleColumnName
import dev.cowzy.kuery.reflection.table
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.search(
    query: SearchQuery<T>,
    limit: Int, offset: Int,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    connection: Connection
): List<SearchQueryResult> {
    val distinctBy = distinctModes[query.distinctMode] ?: throw BadDistinctModeException(query.distinctMode)
    return build(query, SearchQueryMode.SEARCH, applyCustomConditions)
        .limit(limit)
        .offset(offset)
        .get(connection) { row, index -> parseResult(distinctBy, row, index) }
}

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.random(
    query: SearchQuery<T>,
    limit: Int,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    connection: Connection
): List<SearchQueryResult> {
    val distinctBy = distinctModes[query.distinctMode] ?: throw BadDistinctModeException(query.distinctMode)
    return build(query, SearchQueryMode.RANDOM, applyCustomConditions)
        .limit(limit)
        .get(connection) { row, index -> parseResult(distinctBy, row, index) }
}

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.count(
    query: SearchQuery<T>,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    connection: Connection
) = build(query, SearchQueryMode.COUNT, applyCustomConditions).single(connection) { row, index -> row.getInt(index.getAndIncrement()) }

data class QueryExecutionResult<T : Enum<T>, Result>(
    val query: SearchQuery<T>,
    val attempt: Int,
    val result: Result?,
)

suspend fun <T : Enum<T>, Result> SearchQueryExecutor<T>.execute(
    query: SearchQuery<T>,
    retry: Boolean = true,
    execute: suspend (SearchQuery<T>) -> Pair<Result, Boolean>
): QueryExecutionResult<T, Result> {
    var attempt = 0
    var transformedQuery: SearchQuery<T> = query
    var result: Result? = null

    do {
        transformedQuery = tryTransform(query, attempt) ?: break
        attempt++

        val (newResult, success) = execute(transformedQuery)
        result = newResult

        if (success) return QueryExecutionResult(transformedQuery, attempt, result)
    } while (retry)

    return QueryExecutionResult(transformedQuery, attempt, result)
}

private suspend fun <T : Enum<T>> SearchQueryExecutor<T>.build(
    query: SearchQuery<T>,
    mode: SearchQueryMode,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
): SelectQueryBuilder {
    val expression = query.normalizedExpression
    val distinctBy = distinctModes[query.distinctMode] ?: throw BadDistinctModeException(query.distinctMode)

    val affectedTables = (expression.collectTables() + config.table + distinctBy.table()).toMutableSet()

    this.customTables?.invoke(query, mode)?.let { affectedTables.addAll(it) }

    val builder = config.table.selectBuilder()
        .distinctOn(distinctBy)
        .selectAs(distinctBy, "id")
        .orderBy(distinctBy)

    if (mode == SearchQueryMode.SEARCH || mode == SearchQueryMode.RANDOM) {
        val sortColumns = query.sorting.mode.properties

        val printIdColumn = config.printIdColumn
        printIdColumn?.let { affectedTables.add(it.table()) }
        builder.selectRaw("${printIdColumn?.columnName()} as printId")

        val faceIndexColumn = config.faceIndexColumn
        faceIndexColumn?.let { affectedTables.add(it.table()) }
        builder.selectRaw("${faceIndexColumn?.columnName()} as faceIndex")

        affectedTables.addAll(query.sorting.mode.properties.map { it.table() })

        val languageColumn = config.languageColumns.firstOrNull { affectedTables.contains(it.table()) }
        builder.selectRaw("${languageColumn?.columnName()} as language")

        if (mode == SearchQueryMode.SEARCH) {
            sortColumns.forEach { column ->
                val type = column.returnType.classifier as KClass<*>
                val sortName = "sort_${column.simpleColumnName()}"
                if (type.isSubclassOf(Number::class)) {
                    // TODO: This is a hack to make sure that null values are sorted last.
                    builder.selectAs("COALESCE(${column.columnName()}, 2147483647)", sortName)
                } else {
                    builder.selectAs(column.columnName(), sortName)
                }
            }
        }
    }

    builder.applyJoins(affectedTables, config)

    val properties = expression.collectProperties()
    properties.forEach { it.applyProperty(builder) }

    builder.whereSuspend {
        it.applyExpression(expression, distinctBy)
    }

    applyCustomConditions?.invoke(builder)
    this.customBuilder?.invoke(query, mode, builder)

    return when (mode) {
        SearchQueryMode.RANDOM -> QueryBuilder.selectBuilder(builder.toSqlExpression(), "innerQuery")
            .select("innerQuery.id")
            .select("innerQuery.printId")
            .select("innerQuery.faceIndex")
            .select("innerQuery.language")
            .orderByRaw("RANDOM()")

        SearchQueryMode.SEARCH -> QueryBuilder.selectBuilder(builder.toSqlExpression(), "innerQuery")
            .select("innerQuery.id")
            .select("innerQuery.printId")
            .select("innerQuery.faceIndex")
            .select("innerQuery.language")
            .also { searchBuilder ->
                val sortColumns = query.sorting.mode.properties

                sortColumns.forEach {
                    searchBuilder.orderBy("innerQuery.sort_${it.simpleColumnName()}", query.sorting.order)
                }

                searchBuilder.orderBy("innerQuery.id", query.sorting.order)
            }

        SearchQueryMode.COUNT -> QueryBuilder
            .selectBuilder(builder.toSqlExpression(), "innerQuery")
            .selectCount()
    }
}

private fun <T : Enum<T>> SearchQueryExecutor<T>.parseResult(
    distinctBy: KProperty1<*, UUID>,
    row: ResultSet,
    index: ColumnIndex
): SearchQueryResult {
    val id = distinctBy.parse(row, index)

    val printId = config.printIdColumn?.parse(row, index)
    if (config.printIdColumn == null) index.getAndIncrement()

    val faceIndex = config.faceIndexColumn?.parse(row, index)
    if (config.faceIndexColumn == null) index.getAndIncrement()

    val language = row.getString(index.getAndIncrement())

    return SearchQueryResult(
        id = id,
        matchedPrintId = printId,
        matchedFaceIndex = faceIndex,
        matchedLanguage = language
    )
}

private suspend fun <T : WhereQueryBuilder<T>> T.applyExpression(
    expression: QueryExpression,
    distinctBy: KProperty1<*, *>
) {
    when (expression) {
        is BooleanQueryExpression -> this.whereRaw(if (expression.negate) "FALSE" else "TRUE")
        is FilterLeafQueryExpression -> when {
            expression.negate -> this.whereNotSuspend {
                expression.property.applyCondition(
                    it,
                    expression.operator,
                    expression.otherProperty
                )
            }

            else -> expression.property.applyCondition(this, expression.operator, expression.otherProperty)
        }

        is ValueLeafQueryExpression -> when {
            expression.negate -> this.whereNotSuspend {
                expression.property.applyCondition(
                    it,
                    expression.operator,
                    expression.value
                )
            }

            else -> expression.property.applyCondition(this, expression.operator, expression.value)
        }

        is QueryExpressionGroup -> {
            if (expression.children.isEmpty()) return

            val applyChildren: suspend (ConcreteWhereQueryBuilder) -> Unit = { builder ->
                val valueExpressions = expression.children.filterIsInstance<ValueLeafQueryExpression>().toSet()
                val valueExpressionsByProperty = valueExpressions.groupBy { it.property to it.negate }

                valueExpressionsByProperty.forEach { (key, children) ->
                    val (property, negate) = key

                    if (children.size > 1) {
                        val conditions = children.map { it.operator to it.value }

                        if (expression.operator == LogicalOperator.AND && property.handleJoinedAnd) {
                            when (negate) {
                                true -> builder.whereNotSuspend { property.applyMultipleConditions(builder, LogicalOperator.AND, conditions) }
                                false -> builder.whereSuspend { property.applyMultipleConditions(builder, LogicalOperator.AND, conditions) }
                            }
                            return@forEach
                        } else if (expression.operator == LogicalOperator.OR && property.handleJoinedOr) {
                            when (negate) {
                                true -> builder.orWhereNotSuspend { property.applyMultipleConditions(builder, LogicalOperator.OR, conditions) }
                                false -> builder.orWhereSuspend { property.applyMultipleConditions(builder, LogicalOperator.OR, conditions) }
                            }
                            return@forEach
                        }
                    }

                    children.forEach { child ->
                        if (expression.operator == LogicalOperator.AND) {
                            builder.whereSuspend { it.applyExpression(child, distinctBy) }
                        } else {
                            builder.orWhereSuspend { it.applyExpression(child, distinctBy) }
                        }
                    }
                }

                val otherExpressions = expression.children - valueExpressions

                otherExpressions.forEach { filter ->
                    if (expression.operator == LogicalOperator.AND) {
                        builder.whereSuspend { it.applyExpression(filter, distinctBy) }
                    } else {
                        builder.orWhereSuspend { it.applyExpression(filter, distinctBy) }
                    }
                }
            }

            if (expression.negate) {
                this.whereNotSuspend(applyChildren)
            } else {
                this.whereSuspend(applyChildren)
            }
        }
    }
}

fun SelectQueryBuilder.applyJoins(
    tables: Set<KClass<*>>,
    config: SearchQueryConfig
): SelectQueryBuilder {
    val joinedTables = mutableSetOf(config.table)

    tables.forEach {
        if (joinedTables.contains(it)) return@forEach

        val localJoins = mutableListOf<(SelectQueryBuilder) -> Unit>()
        var current = arrayOf(it)
        do {
            current = current.mapNotNull { table ->
                if (joinedTables.contains(table)) return@mapNotNull null
                val dependency = config.tableDependencies[table]!!
                localJoins.add(dependency.join)
                joinedTables.add(table)
                dependency.tables
            }.flatten().toTypedArray()
        } while (current.isNotEmpty())

        localJoins.reversed().forEach { it(this) }
    }

    return this
}

private fun QueryExpression.collectTables(): Set<KClass<*>> {
    return when (this) {
        is FilterLeafQueryExpression -> (this.property.affectedTables + this.otherProperty.affectedTables).toSet()
        is ValueLeafQueryExpression -> this.property.affectedTables.toSet()
        is QueryExpressionGroup -> this.children.map { it.collectTables() }.flatten().toSet()
        else -> emptySet()
    }
}

private fun QueryExpression.collectProperties(): Set<SearchQueryProperty<out Any>> {
    return when (this) {
        is FilterLeafQueryExpression -> setOf(this.property, this.otherProperty)
        is ValueLeafQueryExpression -> setOf(this.property)
        is QueryExpressionGroup -> this.children.map { it.collectProperties() }.flatten().toSet()
        else -> emptySet()
    }
}
