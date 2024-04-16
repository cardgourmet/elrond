package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import java.util.UUID
import kotlin.reflect.KProperty1

class UuidColumnProperty(
    private val column: KProperty1<*, UUID?>,
    descriptor: PropertyDescriptor
) : SearchQueryProperty<UUID>(stringQueryOperators, emptyArray(), arrayOf(column.table()), descriptor) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            transform { value ->
                try {
                    UUID.fromString(value.value)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T, operator: SearchQueryOperator, value: UUID) {
        builder.where(column, value)
    }
}