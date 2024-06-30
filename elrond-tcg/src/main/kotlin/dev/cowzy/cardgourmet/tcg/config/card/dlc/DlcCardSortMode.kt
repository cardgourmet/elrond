package dev.cowzy.cardgourmet.tcg.config.card.dlc

import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCard
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.SortMode
import dev.cowzy.kuery.Order
import kotlinx.serialization.SerialName
import kotlin.reflect.KProperty1

enum class DlcCardSortMode(
    override val keywords: Array<String>,
    override val properties: Array<KProperty1<*, *>>,
    override val defaultOrder: Order = Order.ASCENDING
) : SortMode {

    @SerialName("name") NAME(arrayOf("name"), DlcCardTranslation::simpleName),
    @SerialName("set") SET_CODE(arrayOf("set"), arrayOf(DlcSet::code, DlcPrint::collectorNumberValue, DlcPrint::collectorNumber)),
    @SerialName("ink") INK_TYPE(arrayOf("ink", "color"), arrayOf(DlcCard::inkType)),
    @SerialName("strength") STRENGTH(arrayOf("strength", "power"), arrayOf(DlcCard::strength)),
    @SerialName("willpower") WILLPOWER(arrayOf("willpower", "toughness"), arrayOf(DlcCard::willpower)),
    @SerialName("movement") MOVEMENT_COST(arrayOf("movement"), DlcCard::moveCost),
    @SerialName("released") RELEASE_DATE(arrayOf("released"), arrayOf(DlcSet::releaseDate, DlcSet::code, DlcPrint::collectorNumberValue, DlcPrint::collectorNumber), Order.DESCENDING);

//    RARITY("rarity", MtgPrint::rarity),
//    PRICE_EUR("eur", MtgPrintPrice::priceEur),
//    PRICE_USD("usd", MtgPrintPrice::priceUsd),
//    PRICE_TIX("tix", MtgPrintPrice::priceTix);

    constructor(keywords: Array<String>, property: KProperty1<*, *>, defaultOrder: Order = Order.ASCENDING) : this(
        keywords,
        arrayOf(property),
        defaultOrder
    )

}