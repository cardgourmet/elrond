package elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import kotlin.reflect.KClass

abstract class NumericSearchQueryProperty(
    affectedTables: Array<KClass<*>>,
    descriptorSubjectKey: String
) : SearchQueryProperty<Number>(
    supportedOperators = numericQueryOperators,
    comparableTo = arrayOf(NumericSearchQueryProperty::class),
    affectedTables = affectedTables,
    descriptor = NumericDescriptor(descriptorSubjectKey)
) {

    override val valueDefinition = QueryValueDefinition {
        NumberValue::class {
            transform { it.value }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: Number
    ) {
        builder.where(getRawSql(), operator.toNumericSqlOperator(), value)
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        other: SearchQueryProperty<*>
    ) {
        if (other !is NumericSearchQueryProperty) {
            throw IllegalStateException("Unsupported property type: ${other::class.simpleName}")
        }

        builder.whereRaw(getRawSql(), operator.toNumericSqlOperator(), other.getRawSql())
    }

    abstract fun getRawSql(): String

}
