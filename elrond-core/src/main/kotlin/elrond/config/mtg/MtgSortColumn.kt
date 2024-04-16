package elrond.config.mtg

import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import elrond.SortColumnDefinition
import kotlin.reflect.KProperty1

enum class MtgSortColumn(override val keyword: String, override val properties: Array<KProperty1<*, *>>) :
    elrond.SortColumnDefinition {

    NAME("name", MtgCardFaceTranslation::sortName),
    RARITY("rarity", MtgPrint::rarity),
    SET_CODE("set_code", MtgPrint::setCode),
    COLLECTOR_NUMBER("collector_number", arrayOf(MtgPrint::collectorNumber, MtgPrint::collectorNumberValue)),
    MANA_VALUE("mana_value", MtgCardFace::manaValue),
    COLOR("colors", MtgCardFace::colorSort),
    POWER("power", arrayOf(MtgCardFace::powerValue, MtgCardFace::powerDisplay)),
    TOUGHNESS("toughness", arrayOf(MtgCardFace::toughnessValue, MtgCardFace::toughnessDisplay)),
    DEFENSE("defense", arrayOf(MtgCardFace::defenseValue, MtgCardFace::defenseDisplay)),
    LOYALTY("loyalty", arrayOf(MtgCardFace::loyaltyValue, MtgCardFace::loyaltyDisplay)),
    RELEASE_DATE("released_at", MtgPrint::releaseDate),
    EDHREC_RANK("edhrec_rank", MtgCard::edhrecRank),
    PRICE_EUR("price_eur", MtgPrintPrice::priceEur),
    PRICE_USD("price_usd", MtgPrintPrice::priceUsd),
    PRICE_TIX("price_tix", MtgPrintPrice::priceTix);

    constructor(keyword: String, property: KProperty1<*, *>) : this(keyword, arrayOf(property))

}
