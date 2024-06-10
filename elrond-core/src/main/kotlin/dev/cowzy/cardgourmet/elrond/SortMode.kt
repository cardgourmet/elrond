package dev.cowzy.cardgourmet.elrond

import dev.cowzy.kuery.Order
import kotlin.reflect.KProperty1

interface SortMode {
    val keywords: Array<String>
    val properties: Array<KProperty1<*, *>>
    val defaultOrder: Order
}

data class Sorting(val mode: SortMode, val order: Order)
