package dev.cowzy.cardgourmet.elrond.config.mtg

import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.elrond.SortMode
import dev.cowzy.kuery.Order
import kotlin.reflect.KProperty1

enum class MtgSortMode(
    override val keywords: Array<String>,
    override val properties: Array<KProperty1<*, *>>,
    override val defaultOrder: Order = Order.ASCENDING
) : SortMode {

    MANA_VALUE("cmc", MtgCardFace::manaValue),
    POWER("power", arrayOf(MtgCardFace::powerValue, MtgCardFace::powerDisplay)),
    TOUGHNESS("toughness", arrayOf(MtgCardFace::toughnessValue, MtgCardFace::toughnessDisplay)),
    DEFENSE("defense", arrayOf(MtgCardFace::defenseValue, MtgCardFace::defenseDisplay)),
    LOYALTY("loyalty", arrayOf(MtgCardFace::loyaltyValue, MtgCardFace::loyaltyDisplay)),
    SET("set", arrayOf(MtgPrint::setCode, MtgPrint::collectorNumberValue, MtgPrint::collectorNumber)),
    NAME("name", MtgCardFaceTranslation::sortName),
    PRICE_USD("usd", MtgPrintPrice::priceUsd),
    PRICE_TIX("tix", MtgPrintPrice::priceTix),
    PRICE_EUR("eur", MtgPrintPrice::priceEur),
    RARITY("rarity", MtgPrint::rarity),
    COLOR("color", MtgCardFace::colorSort),
    RELEASE_DATE("released", MtgPrint::releaseDate, Order.DESCENDING),
    EDHREC_RANK("edhrec", MtgCard::edhrecRank);

    // Scryfall sorting not supported by cardgourmet as of now:
    // - spoiled
    // - artist
    // - penny
    // - review

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