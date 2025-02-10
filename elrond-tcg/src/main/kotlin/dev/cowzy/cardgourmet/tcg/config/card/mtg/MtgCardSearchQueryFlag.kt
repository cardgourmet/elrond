package dev.cowzy.cardgourmet.tcg.config.card.mtg

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
enum class MtgCardSearchQueryFlag {

    @SerialName("include:extras") INCLUDE_EXTRAS,
    @SerialName("lang:any") ANY_LANGUAGE,
    @SerialName("require:image") REQUIRE_IMAGE,
    @SerialName("prefer:basic") PREFER_BASIC,
    @SerialName("prefer:special") PREFER_SPECIAL,
    @SerialName("prefer:oldest") PREFER_OLDEST,
    @SerialName("prefer:newest") @JsonNames("prefer:newest", "prefer:latest") PREFER_NEWEST,
    @SerialName("prefer:promo") PREFER_PROMO,
    @SerialName("prefer:arena") @JsonNames("prefer:alchemy", "prefer:arena") PREFER_ARENA,
    @SerialName("prefer:usd-low") PREFER_USD_LOW,
    @SerialName("prefer:usd-high") PREFER_USD_HIGH,
    @SerialName("prefer:eur-low") PREFER_EUR_LOW,
    @SerialName("prefer:eur-high") PREFER_EUR_HIGH,
    @SerialName("prefer:tix-low") PREFER_TIX_LOW,
    @SerialName("prefer:tix-high") PREFER_TIX_HIGH;

    companion object {
        val preferModes = setOf(
            PREFER_OLDEST,
            PREFER_NEWEST,
            PREFER_BASIC,
            PREFER_SPECIAL,
            PREFER_PROMO,
            PREFER_ARENA,
            PREFER_EUR_HIGH,
            PREFER_EUR_LOW,
            PREFER_USD_HIGH,
            PREFER_USD_LOW,
            PREFER_TIX_HIGH,
            PREFER_TIX_LOW
        )

        val costPreferModes = setOf(
            PREFER_EUR_HIGH,
            PREFER_EUR_LOW,
            PREFER_USD_HIGH,
            PREFER_USD_LOW,
            PREFER_TIX_HIGH,
            PREFER_TIX_LOW
        )
    }

}
