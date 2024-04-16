package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.kuery.query.SelectQueryBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class SearchQueryConfig(
    val table: KClass<*>,
    val printIdColumn: KProperty1<*, *>?,
    val faceIndexColumn: KProperty1<*, *>?,
    val languageColumns: Array<KProperty1<*, *>> = emptyArray(),
    val tableDependencies: Map<KClass<*>, TableDependency> = emptyMap()
)

interface CollectionSearchQueryConfig

data class TableDependency(
    val tables: List<KClass<*>>,
    val join: (SelectQueryBuilder) -> Unit
) {
    constructor(vararg tables: KClass<*>, join: (SelectQueryBuilder) -> Unit) : this(tables = tables.toList(), join = join)
}

@Serializable
enum class SearchQueryDistinctMode(val key: String) {

    @SerialName("unique:cards")
    UNIQUE_CARDS(Strings.Query.Subject.Cards.KEY),

    @SerialName("unique:prints")
    UNIQUE_PRINTS(Strings.Query.Subject.Prints.KEY),

    @SerialName("unique:faces")
    UNIQUE_FACES(Strings.Query.Subject.Faces.KEY),

    @SerialName("unique:printfaces")
    UNIQUE_PRINT_FACES(Strings.Query.Subject.PrintFaces.KEY),

    @SerialName("unique:art")
    UNIQUE_ART(Strings.Query.Subject.Prints.KEY),

}