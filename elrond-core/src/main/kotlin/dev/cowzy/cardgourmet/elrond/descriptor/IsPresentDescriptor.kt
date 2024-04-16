package dev.cowzy.cardgourmet.elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.Strings

class IsPresentDescriptor(
    propertyKey: String,
    inverted: Boolean = false,
) : SimplePropertyDescriptor(
    comparisonKey = Strings.Query.Comparison.IsPresent.KEY,
    propertyKey = propertyKey,
    inverted = inverted
)
