package dev.cowzy.cardgourmet.elrond.config.mtg

import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.elrond.SortMode
import dev.cowzy.kuery.Order
import kotlinx.serialization.SerialName
import kotlin.reflect.KProperty1

enum class MtgSortMode(
    override val keywords: Array<String>,
    override val properties: Array<KProperty1<*, *>>,
    override val defaultOrder: Order = Order.ASCENDING
) : SortMode {

    @SerialName("cmc") MANA_VALUE("cmc", MtgCardFace::manaValue),
    @SerialName("power") POWER("power", arrayOf(MtgCardFace::powerValue, MtgCardFace::powerDisplay)),
    @SerialName("toughness") TOUGHNESS("toughness", arrayOf(MtgCardFace::toughnessValue, MtgCardFace::toughnessDisplay)),
    @SerialName("defense") DEFENSE("defense", arrayOf(MtgCardFace::defenseValue, MtgCardFace::defenseDisplay)),
    @SerialName("loyalty") LOYALTY("loyalty", arrayOf(MtgCardFace::loyaltyValue, MtgCardFace::loyaltyDisplay)),
    @SerialName("set") SET("set", arrayOf(MtgPrint::setCode, MtgPrint::collectorNumberValue, MtgPrint::collectorNumber)),
    @SerialName("name") NAME("name", MtgCardFaceTranslation::sortName),
    @SerialName("usd") PRICE_USD("usd", MtgPrintPrice::priceUsd),
    @SerialName("tix") PRICE_TIX("tix", MtgPrintPrice::priceTix),
    @SerialName("eur") PRICE_EUR("eur", MtgPrintPrice::priceEur),
    @SerialName("rarity") RARITY("rarity", MtgPrint::rarity),
    @SerialName("color") COLOR("color", MtgCardFace::colorSort),
    @SerialName("released") RELEASE_DATE("released", MtgPrint::releaseDate, Order.DESCENDING),
    @SerialName("edhrec") EDHREC_RANK("edhrec", MtgCard::edhrecRank);

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