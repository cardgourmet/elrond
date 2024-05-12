package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.values.PropertyProvider
import dev.cowzy.cardgourmet.elrond.values.PropertyProviderPool
import kotlin.reflect.KProperty1

class ArrayCardinalityProperty(
    private vararg val columns: KProperty1<*, *>,
    propertyKey: String
) : NumericSearchQueryProperty(
    affectedTables = columns.map { it.table() }.distinct().toTypedArray(),
    descriptorSubjectKey = propertyKey
) {

    override val valueDefinition = QueryValueDefinition {
        NumberValue::class {
            transform { it.value }
            match { (it as Number).toDouble() >= 0 }
        }
    }

    override fun getRawSql() = columns.joinToString(" + ") { "cardinality(${it.columnName()})" }

}