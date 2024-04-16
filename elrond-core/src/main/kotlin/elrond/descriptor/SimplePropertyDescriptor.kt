package elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression

open class SimplePropertyDescriptor(
    private val trueComparisonKey: String,
    private val falseComparisonKey: String,
    private val inverted: Boolean = false,
    propertyKey: String,
) : PropertyDescriptor(propertyKey) {

    constructor(comparisonKey: String, propertyKey: String, inverted: Boolean = false) : this(
        trueComparisonKey = "$comparisonKey.true",
        falseComparisonKey = "$comparisonKey.false",
        inverted = inverted,
        propertyKey = propertyKey
    )

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        val negated = if (inverted == negate) expression.negate else !expression.negate
        val comparisonKey = if (negated) falseComparisonKey else trueComparisonKey
        return i18n.translate(locale, comparisonKey, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}