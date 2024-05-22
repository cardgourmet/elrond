package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.elrond.ElrondSortColumn
import dev.cowzy.cardgourmet.elrond.config.MaterializedViewMappings
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.snakeCaseColumnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.kuery.query.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

data class SearchQuery<T : Enum<T>>(
    val mode: SearchQueryMode,
    val expression: QueryExpression,
    val distinctBy: KProperty1<*, *>,
    val sortColumns: List<ElrondSortColumn>,
    val flags: Set<T>,
    val preferredLanguage: String?
)

enum class SearchQueryMode {
    SEARCH,
    COUNT
}

data class SearchQuerySqlBuilder<T : Enum<T>>(
    val affectedTables: ((SearchQuery<T>) -> Set<KClass<*>>)? = null,
    val apply: ((SearchQuery<T>, SelectQueryBuilder) -> Unit)? = null
)

suspend fun <T : Enum<T>> SearchQuery<T>.toQueryBuilder(
    config: SearchQueryConfig,
    distinctBy: KProperty1<*, *>,
    sortColumns: List<ElrondSortColumn>,
    sqlBuilder: SearchQuerySqlBuilder<T>? = null,
    applyCustomConditions: ((SelectQueryBuilder) -> Unit)? = null
): SelectQueryBuilder {
    val expression = this.expression
    val faceIndexColumn = config.faceIndexColumn
    val printIdColumn = config.printIdColumn

    val affectedTables = (expression.collectTables()
            + (sqlBuilder?.affectedTables?.invoke(this) ?: emptySet())
            + sortColumns.map { it.property.table() }
            + config.table
            + distinctBy.table()
            + (faceIndexColumn?.let { setOf(it.table()) } ?: emptySet()))

    val languageColumn = config.languageColumns.firstOrNull { affectedTables.contains(it.table()) }

    val builder = config.table.selectBuilder()
        .distinctOn(distinctBy)
        .selectAs(distinctBy, "distinct_id")
        .selectRaw("${languageColumn?.columnName()} as language")
        .selectRaw("${faceIndexColumn?.columnName()} as faceIndex")
        .selectRaw("${printIdColumn?.columnName()} as printId")
        .orderBy(distinctBy)

    sortColumns.forEach { column ->
        val property = column.property
        val type = property.returnType.classifier as KClass<*>
        if (type.isSubclassOf(Number::class)) {
            // TODO: This is a hack to make sure that null values are sorted last.
            builder.selectAs("COALESCE(${property.columnName()}, 2147483647)", column.sortName)
        } else {
            builder.selectAs(property.columnName(), column.sortName)
        }
    }

    builder.applyJoins(affectedTables, config)

    val properties = expression.collectProperties()
    properties.forEach { it.applyProperty(builder) }

    builder.whereSuspend {
        val inColumns = listOfNotNull(distinctBy, languageColumn, faceIndexColumn, printIdColumn)
        it.applyExpression(inColumns, expression, distinctBy)
    }

    // Apply custom selects, joins, where conditions, order expressions etc.
    sqlBuilder?.apply?.invoke(this, builder)
    applyCustomConditions?.invoke(builder)

    return builder
}

private suspend fun <T : WhereQueryBuilder<T>> T.applyExpression(
    inColumns: List<KProperty1<*, *>>,
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
                if (expression.operator == LogicalOperator.AND) {
                    expression.children.map { child ->
                        builder.whereSuspend { it.applyExpression(inColumns, child, distinctBy) }
                    }
                } else {
                    expression.children.map { child ->
                        builder.orWhereSuspend { it.applyExpression(inColumns, child, distinctBy) }
                    }

//                    val builders = expression.children.map { child ->
//                        baseBuilder.clone().whereSuspend { it.applyExpression(baseBuilder, inColumns, child, distinctBy) }
//                    }
//
//                    val query = if (builders.size > 1) {
//                        var unionQuery = builders.first().union(builders[1])
//                        builders.drop(2).forEach { unionQuery = unionQuery.union(it) }
//                        unionQuery.toSqlExpression()
//                    } else if (builders.size == 1) {
//                        builders.first().toSqlExpression()
//                    } else {
//                        throw IllegalStateException("Empty group")
//                    }
//
//                    builder.whereInRaw("(${inColumns.joinToString { it.columnName() }})", "(${query.sql})", query.fill)
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
    config: SearchQueryConfig,
    materializedViewMappings: MaterializedViewMappings? = null
): SelectQueryBuilder {
    val joinedTables = mutableSetOf(config.table) + materializedViewMappings?.keys.orEmpty()

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

private fun QueryExpression.collectProperties(): Set<SearchQueryProperty<out Any>> {
    return when (this) {
        is FilterLeafQueryExpression -> setOf(this.property, this.otherProperty)
        is ValueLeafQueryExpression -> setOf(this.property)
        is QueryExpressionGroup -> this.children.map { it.collectProperties() }.flatten().toSet()
        else -> emptySet()
    }
}

private fun QueryExpression.collectTables(): Set<KClass<*>> {
    return when (this) {
        is FilterLeafQueryExpression -> (this.property.affectedTables + this.otherProperty.affectedTables).toSet()
        is ValueLeafQueryExpression -> this.property.affectedTables.toSet()
        is QueryExpressionGroup -> this.children.map { it.collectTables() }.flatten().toSet()
        else -> emptySet()
    }
}
