package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.elrond.BadDistinctModeException
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.SearchQuerySqlConfig
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.tokenizer.LogicalOperator
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

suspend fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.search(
    query: SearchQuery<SearchFlag, DistinctMode>,
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

suspend fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.random(
    query: SearchQuery<SearchFlag, DistinctMode>,
    limit: Int,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    connection: Connection
): List<SearchQueryResult> {
    val distinctBy = distinctModes[query.distinctMode] ?: throw BadDistinctModeException(query.distinctMode)
    return build(query, SearchQueryMode.RANDOM, applyCustomConditions)
        .limit(limit)
        .get(connection) { row, index -> parseResult(distinctBy, row, index) }
}

suspend fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.count(
    query: SearchQuery<SearchFlag, DistinctMode>,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
    connection: Connection
) = build(query, SearchQueryMode.COUNT, applyCustomConditions).single(connection) { row, index -> row.getInt(index.getAndIncrement()) }

data class QueryExecutionResult<SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>, Result>(
    val query: SearchQuery<SearchFlag, DistinctMode>,
    val attempt: Int,
    val result: Result?,
)

suspend fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>, Result> SearchQueryExecutor<SearchFlag, DistinctMode>.execute(
    query: SearchQuery<SearchFlag, DistinctMode>,
    retry: Boolean = true,
    execute: suspend (SearchQuery<SearchFlag, DistinctMode>) -> Pair<Result, Boolean>
): QueryExecutionResult<SearchFlag, DistinctMode, Result> {
    var attempt = 0
    var transformedQuery: SearchQuery<SearchFlag, DistinctMode> = query
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

suspend fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.build(
    query: SearchQuery<SearchFlag, DistinctMode>,
    mode: SearchQueryMode,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null,
): SelectQueryBuilder {
    val expression = query.normalizedExpression
    val distinctBy = distinctModes[query.distinctMode] ?: throw BadDistinctModeException(query.distinctMode)

    val affectedTables = (expression.collectTables() + config.baseTable + distinctBy.table()).toMutableSet()

    this.customTables?.invoke(query, mode)?.let { affectedTables.addAll(it) }

    val builder = config.baseTable.selectBuilder()
        .distinctOn(distinctBy)
        .selectAs(distinctBy, "id")
        .orderBy(distinctBy)

    if (mode == SearchQueryMode.SEARCH || mode == SearchQueryMode.RANDOM) {
        val sortColumns = query.sorting.mode.properties

        if (mode == SearchQueryMode.SEARCH) {
            affectedTables.addAll(sortColumns.map { it.table() })

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

        config.customFields.entries.sortedBy { it.key }.forEach { (key, field) ->
            val property = field.properties.firstOrNull { affectedTables.contains(it.table()) } ?: field.properties.firstOrNull()
            property?.let { affectedTables.add(it.table()) }
            builder.selectRaw("${property?.columnName()} as $key")
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
            .apply { config.customFields.keys.forEach { key -> select("innerQuery.$key") } }
            .orderByRaw("RANDOM()")

        SearchQueryMode.SEARCH -> QueryBuilder.selectBuilder(builder.toSqlExpression(), "innerQuery")
            .select("innerQuery.id")
            .apply { config.customFields.keys.forEach { key -> select("innerQuery.$key") } }
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

private fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.parseResult(
    distinctBy: KProperty1<*, UUID>,
    row: ResultSet,
    index: ColumnIndex
): SearchQueryResult {
    val id = distinctBy.parse(row, index)

    val customFields = config.customFields.mapValues { (key, field) ->
        val property = field.properties.firstOrNull()

        if (property == null) {
            index.getAndIncrement()
            return@mapValues null
        }

        return@mapValues property.parse(row, index)
    }

    return SearchQueryResult(
        id = id,
        customFields = customFields
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

        is MultiValueLeafQueryExpression -> {
            this.applyExpression(QueryExpressionGroup(
                expression.properties.map {
                    ValueLeafQueryExpression(
                        expression.filter,
                        it.property,
                        it.operator,
                        it.value,
                        false,
                        expression.valueToken,
                    )
                },
                LogicalOperator.OR,
                expression.negate
            ), distinctBy)
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

                        when {
                            expression.operator == LogicalOperator.AND && negate && property.handleJoinedOr -> {
                                builder.whereNotSuspend { property.applyMultipleConditions(it, LogicalOperator.OR, conditions) }
                                return@forEach
                            }

                            expression.operator == LogicalOperator.AND && !negate && property.handleJoinedAnd -> {
                                builder.whereSuspend { property.applyMultipleConditions(it, LogicalOperator.AND, conditions) }
                                return@forEach
                            }

                            expression.operator == LogicalOperator.OR && negate && property.handleJoinedAnd -> {
                                builder.orWhereNotSuspend { property.applyMultipleConditions(it, LogicalOperator.AND, conditions) }
                                return@forEach
                            }

                            expression.operator == LogicalOperator.OR && !negate && property.handleJoinedOr -> {
                                builder.orWhereSuspend { property.applyMultipleConditions(it, LogicalOperator.OR, conditions) }
                                return@forEach
                            }
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
    config: SearchQuerySqlConfig
): SelectQueryBuilder {
    val joinedTables = mutableSetOf(config.baseTable)

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
        is MultiValueLeafQueryExpression -> this.properties.flatMap { it.property.affectedTables.toSet() }.toSet()
        is QueryExpressionGroup -> this.children.flatMap { it.collectTables() }.toSet()
        else -> emptySet()
    }
}

private fun QueryExpression.collectProperties(): Set<SearchQueryProperty<out Any>> {
    return when (this) {
        is FilterLeafQueryExpression -> setOf(this.property, this.otherProperty)
        is ValueLeafQueryExpression -> setOf(this.property)
        is MultiValueLeafQueryExpression -> this.properties.map { it.property }.toSet()
        is QueryExpressionGroup -> this.children.flatMap { it.collectProperties() }.toSet()
        else -> emptySet()
    }
}
