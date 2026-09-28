package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.ExecutionContext
import kotlin.reflect.KClass

open class NumericExpressionProperty(
    private val getExpression: (ExecutionContext) -> String,
    affectedTables: Array<KClass<*>>,
    descriptorSubjectKey: String
) : NumericSearchQueryProperty(
    affectedTables = affectedTables,
    descriptorSubjectKey = descriptorSubjectKey
) {
    override fun getRawSql(ctx: ExecutionContext) = getExpression(ctx)
}