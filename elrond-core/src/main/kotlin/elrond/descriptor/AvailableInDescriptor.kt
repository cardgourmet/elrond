package elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.Strings

class AvailableInDescriptor(propertyKey: String) : SimplePropertyDescriptor(
    comparisonKey = Strings.Query.Comparison.AvailableIn.KEY,
    propertyKey = propertyKey
)
