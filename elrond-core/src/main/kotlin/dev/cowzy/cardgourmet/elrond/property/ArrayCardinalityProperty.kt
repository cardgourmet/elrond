package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import kotlin.reflect.KProperty1

class ArrayCardinalityProperty(
    private vararg val columns: KProperty1<*, *>,
    mappings: Map<String, Pair<Number, SearchQueryOperator>>? = null,
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

        if (mappings != null) {
            StringValue::class {
                useStrictValues = true
                mappingsWithOperator(mappings)
            }
        }
    }

    override fun getRawSql() = columns.joinToString(" + ") { "cardinality(${it.columnName()})" }

}