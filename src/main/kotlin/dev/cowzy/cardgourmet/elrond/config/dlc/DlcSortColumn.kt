package dev.cowzy.cardgourmet.elrond.config.dlc

import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCard
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.SortColumnDefinition
import kotlin.reflect.KProperty1

enum class DlcSortColumn(override val keyword: String, override val properties: Array<KProperty1<*, *>>) : SortColumnDefinition {

    NAME("name", DlcCardTranslation::simpleName),
//    RARITY("rarity", MtgPrint::rarity),
    SET_CODE("set_code", DlcSet::code),
    COLLECTOR_NUMBER("collector_number", arrayOf(DlcPrint::collectorNumber, DlcPrint::collectorNumberValue)),
    INK_TYPE("inkType", DlcCard::inkType),
    STRENGTH("power", DlcCard::strength),
    WILLPOWER("toughness", DlcCard::willpower),
    MOVEMENT_COST("defense", DlcCard::moveCost),
    RELEASE_DATE("released_at", DlcSet::releaseDate);
//    PRICE_EUR("price_eur", MtgPrintPrice::priceEur),
//    PRICE_USD("price_usd", MtgPrintPrice::priceUsd),
//    PRICE_TIX("price_tix", MtgPrintPrice::priceTix);

    constructor(keyword: String, property: KProperty1<*, *>) : this(keyword, arrayOf(property))

}
