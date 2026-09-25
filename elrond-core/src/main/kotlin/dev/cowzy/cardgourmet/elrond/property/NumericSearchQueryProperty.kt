package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.kuery.query.whereNotNull
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
        value: Number,
        ctx: ColumnContext
    ) {
        builder.whereNotNull(getRawSql(ctx))
        builder.where(getRawSql(ctx), operator.toNumericSqlOperator(), value)
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        other: SearchQueryProperty<*>,
        ctx: ColumnContext
    ) {
        if (other !is NumericSearchQueryProperty) {
            throw IllegalStateException("Unsupported property type: ${other::class.simpleName}")
        }

        builder.where { inner ->
            inner.whereNotNull(getRawSql(ctx))
            inner.whereNotNull(other.getRawSql(ctx))
            inner.whereRaw(getRawSql(ctx), operator.toNumericSqlOperator(), other.getRawSql(ctx))
        }.orWhere { inner ->
            inner.whereNull(getRawSql(ctx))
            inner.whereNull(other.getRawSql(ctx))
        }

    }

    abstract fun getRawSql(ctx: ColumnContext): String

}
