package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCard
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.SortMode
import dev.cowzy.kuery.Order
import kotlin.reflect.KProperty1

enum class PcgSortMode(
    override val keywords: Array<String>,
    override val properties: Array<KProperty1<*, *>>,
    override val defaultOrder: Order = Order.ASCENDING
) : SortMode {

    NAME("name", PcgCardTranslation::simpleName),
    RARITY("rarity", PcgPrint::rarityValue),
    SET_CODE("set_code", PcgSet::setCode),
    COLLECTOR_NUMBER("collector_number", arrayOf(PcgPrint::sortValue, PcgPrint::collectorNumberValue, PcgPrint::collectorNumber)),
    SUPER_TYPE("type", PcgCard::superType),
    HEALTH("health", PcgCard::hp),
    RELEASE_DATE("released_at", PcgSet::releaseStartDate, Order.DESCENDING);

//    PRICE_EUR("eur", MtgPrintPrice::priceEur),
//    PRICE_USD("usd", MtgPrintPrice::priceUsd),
//    PRICE_TIX("tix", MtgPrintPrice::priceTix);

    constructor(keyword: String, properties: Array<KProperty1<*, *>>, defaultOrder: Order = Order.ASCENDING) : this(
        arrayOf(keyword),
        properties,
        defaultOrder
    )

    constructor(keyword: String, property: KProperty1<*, *>, defaultOrder: Order = Order.ASCENDING) : this(
        arrayOf(keyword),
        arrayOf(property),
        defaultOrder
    )

}