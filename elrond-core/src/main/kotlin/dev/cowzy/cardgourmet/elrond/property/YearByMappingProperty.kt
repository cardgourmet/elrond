package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import java.time.LocalDate
import kotlin.reflect.KProperty1

class YearByMappingProperty(
    val column: KProperty1<*, *>,
    mappingProvider: ValueProvider<Pair<String, Int>>,
    propertyKey: String,
) : SearchQueryProperty<Int>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = NumericDescriptor(propertyKey)
) {
    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            mappings(mappingProvider)
            useStrictValues = true
            display { it, _, _ -> "`$it`" }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T, operator: SearchQueryOperator, value: Int) {
        builder.where("DATE_PART('Year', ${column.columnName()})", operator.toNumericSqlOperator(), value)
    }
}