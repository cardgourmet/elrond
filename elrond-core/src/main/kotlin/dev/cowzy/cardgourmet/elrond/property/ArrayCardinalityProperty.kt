package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import kotlin.reflect.KProperty1

class ArrayCardinalityProperty(
    private vararg val columns: KProperty1<*, *>,
    private val distinctValues: Boolean = false,
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

    override fun getRawSql(ctx: ExecutionContext) = columns.joinToString(" + ") {
        when {
            distinctValues -> "cardinality(array(select distinct unnest(${ctx.resolve(it)})))"
            else -> "cardinality(${ctx.resolve(it)})"
        }
    }

}
