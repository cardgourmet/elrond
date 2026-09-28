package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.kuery.reflection.*
import kotlin.reflect.*

data class ExecutionContext(
    val attributes: ContextAttributes,
    val searchQuery: SearchQuery<*, *>,
    private val materializedView: MaterializedView?
) {

    fun resolve(column: KProperty1<*, *>): String {
        return materializedView?.columnMappings?.get(column)?.let {
            "${materializedView.table}.${it}"
        } ?: column.columnName()
    }

    fun transformer(column: KProperty1<*, *>) = column.columnTransformer()

    fun placeholder(column: KProperty1<*, *>) = column.placeholder()

    fun resolveTable(table: KClass<*>): String {
        if (materializedView == null) return table.tableName()
        if (materializedView.coveredTables.contains(table)) return materializedView.table
        return table.tableName()
    }

}