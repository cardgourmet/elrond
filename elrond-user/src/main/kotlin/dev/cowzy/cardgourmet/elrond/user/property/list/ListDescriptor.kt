package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression

class ListDescriptor(propertyKey: String) : PropertyDescriptor(propertyKey) {

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        return "PLACEHOLDER"
    }

}