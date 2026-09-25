package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.config.MaterializedView
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.tableName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class ColumnContext(private val materializedView: MaterializedView?) {

    fun resolve(column: KProperty1<*, *>): String {
        return materializedView?.columnMappings?.get(column)?.let {
            "${materializedView.table}.${it}"
        } ?: column.columnName()
    }

    fun resolveTable(table: KClass<*>): String {
        if (materializedView == null) return table.tableName()
        if (materializedView.coveredTables.contains(table)) return materializedView.table
        return table.tableName()
    }

}