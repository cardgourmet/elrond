package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.table
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1

class DateByMappingProperty(
    val column: KProperty1<*, *>,
    override val valueProvider: ValueProvider<String>,
    override val allowAnyValue: Boolean = true,
    private val mappingProvider: ValueProvider<Pair<String, LocalDate>>,
    propertyKey: String,
) : SearchQueryProperty<LocalDate>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = NumericDescriptor(propertyKey)
), ValueProvided {
    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            format = "date"

            transform { value ->
                val matchedValue = matchValue(value.value)
                val mapping = mappingProvider.getValues().find { it.first.equals(matchedValue, ignoreCase = true) } ?: return@transform null
                mapping.second
            }

            display { it, _, _ -> "`${(it as LocalDate).format(DateTimeFormatter.ISO_LOCAL_DATE)}`" }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T, operator: SearchQueryOperator, value: LocalDate) {
        builder.where(column, operator.toNumericSqlOperator(), value)
    }
}