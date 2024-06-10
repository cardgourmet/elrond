package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.config.SearchQueryDistinctMode

class BadDistinctModeException(distinctMode: SearchQueryDistinctMode)
    : Exception("The distinction mode ${distinctMode.getSerialName()} is not supported for this query.")