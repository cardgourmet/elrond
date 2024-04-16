package elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.Strings

class EqualsDescriptor(propertyKey: String) : SimplePropertyDescriptor(
    trueComparisonKey = Strings.Query.Comparison.String.EQUALS,
    falseComparisonKey = Strings.Query.Comparison.String.NOT_EQUALS,
    propertyKey = propertyKey
)
