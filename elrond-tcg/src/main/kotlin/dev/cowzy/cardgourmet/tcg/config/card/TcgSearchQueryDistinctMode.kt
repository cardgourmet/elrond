package dev.cowzy.cardgourmet.tcg.config.card

import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.query.SearchQueryDistinctMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TcgSearchQueryDistinctMode(
    override val keywords: Array<String>,
    override val key: String
) : SearchQueryDistinctMode {

    @SerialName("unique:cards")
    UNIQUE_CARDS(key = Strings.Query.Subject.Cards.KEY),

    @SerialName("unique:prints")
    UNIQUE_PRINTS(key = Strings.Query.Subject.Prints.KEY),

    @SerialName("unique:faces")
    UNIQUE_FACES(key = Strings.Query.Subject.Faces.KEY),

    @SerialName("unique:printfaces")
    UNIQUE_PRINT_FACES(key = Strings.Query.Subject.PrintFaces.KEY),

    @SerialName("unique:art")
    UNIQUE_ART(key = Strings.Query.Subject.Prints.KEY);

    constructor(key: String) : this(keywords = arrayOf(key), key = key)

}