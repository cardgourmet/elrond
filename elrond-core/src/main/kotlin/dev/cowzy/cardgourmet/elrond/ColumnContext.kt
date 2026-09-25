package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.config.MaterializedView
import dev.cowzy.kuery.reflection.columnName
import kotlin.reflect.KProperty1

data class ColumnContext(private val materializedView: MaterializedView?) {
    fun resolve(column: KProperty1<*, *>): String {
        return materializedView?.columnMappings?.get(column) ?: column.columnName()
    }
}