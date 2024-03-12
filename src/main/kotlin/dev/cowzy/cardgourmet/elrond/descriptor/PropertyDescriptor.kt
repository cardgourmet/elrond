package dev.cowzy.cardgourmet.elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.NumberValue
import dev.cowzy.cardgourmet.elrond.RegexValue
import dev.cowzy.cardgourmet.elrond.query.FilterLeafQueryExpression
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression
import kotlin.math.exp

abstract class PropertyDescriptor(val propertyKey: String) {

    abstract suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String

    fun getProperty(locale: UserLanguage, i18n: LocalizationService): String = i18n.translate(locale, propertyKey)

    suspend fun getValue(expression: PropertyQueryExpression, locale: UserLanguage, i18n: LocalizationService): String {
        return when (expression) {
            is ValueLeafQueryExpression -> {
                expression.valueMapping?.let { it.display(expression.value, i18n, locale) } ?: "<missing>"
            }

            is FilterLeafQueryExpression -> {
                expression.otherProperty.descriptor.getProperty(locale, i18n)
            }
        }
    }

}