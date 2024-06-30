package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.getSerialName

class BadDistinctModeException(distinctMode: Enum<*>)
    : Exception("The distinction mode ${distinctMode.getSerialName()} is not supported for this query.")