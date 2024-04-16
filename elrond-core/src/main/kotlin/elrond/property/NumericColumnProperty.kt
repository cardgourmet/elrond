package elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class NumericColumnProperty(
    vararg columns: KProperty1<*, Number?>,
    offset: Double = 0.0,
    propertyKey: String
) : NumericExpressionProperty(
    columns.joinToString(" + ") { it.columnName() }.let {
        when (offset) {
            0.0 -> it
            else -> "($it + $offset)"
        }
    },
    columns.map { it.table() }.distinct().toTypedArray(),
    descriptorSubjectKey = propertyKey
)
