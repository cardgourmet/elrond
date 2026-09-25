package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.ColumnContext
import kotlin.reflect.KClass

open class NumericExpressionProperty(
    private val getExpression: (ColumnContext) -> String,
    affectedTables: Array<KClass<*>>,
    descriptorSubjectKey: String
) : NumericSearchQueryProperty(
    affectedTables = affectedTables,
    descriptorSubjectKey = descriptorSubjectKey
) {
    override fun getRawSql(ctx: ColumnContext) = getExpression(ctx)
}