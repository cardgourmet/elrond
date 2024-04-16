package dev.cowzy.cardgourmet.elrond.property

import kotlin.reflect.KClass

open class NumericExpressionProperty(
    private val expression: String,
    affectedTables: Array<KClass<*>>,
    descriptorSubjectKey: String
) : NumericSearchQueryProperty(
    affectedTables = affectedTables,
    descriptorSubjectKey = descriptorSubjectKey
) {

    override fun getRawSql() = expression

}