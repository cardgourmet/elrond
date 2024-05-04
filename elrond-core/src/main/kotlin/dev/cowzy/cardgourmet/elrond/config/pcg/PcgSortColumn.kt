package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCard
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.SortColumnDefinition
import kotlin.reflect.KProperty1

enum class PcgSortColumn(
    override val keyword: String,
    override val properties: Array<KProperty1<*, *>>
) : SortColumnDefinition {

    NAME("name", PcgCardTranslation::simpleName),
    RARITY("rarity", PcgPrint::rarityValue),
    SET_CODE("set_code", PcgSet::setCode),
    COLLECTOR_NUMBER("collector_number", arrayOf(PcgPrint::collectorNumberValue, PcgPrint::collectorNumber)),
    TYPE("type", PcgCard::type),
    RELEASE_DATE("released_at", PcgSet::releaseStartDate);
//    PRICE_EUR("price_eur", MtgPrintPrice::priceEur),
//    PRICE_USD("price_usd", MtgPrintPrice::priceUsd),
//    PRICE_TIX("price_tix", MtgPrintPrice::priceTix);

    constructor(keyword: String, property: KProperty1<*, *>) : this(keyword, arrayOf(property))

}
