package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import kotlin.reflect.KProperty1

class ArrayCardinalityProperty(
    private val column: KProperty1<*, *>,
    mappings: Map<String, Pair<Number, SearchQueryOperator>>? = null,
    propertyKey: String
) : NumericSearchQueryProperty(
    affectedTables = arrayOf(column.table()),
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

    override fun getRawSql() = "cardinality(${column.columnName()})"

}