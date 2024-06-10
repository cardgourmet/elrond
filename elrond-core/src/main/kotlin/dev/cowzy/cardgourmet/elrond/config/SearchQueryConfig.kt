package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.kuery.expression.SqlExpression
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.tableName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

typealias MaterializedViewMappings = Map<KClass<*>, String>

data class MaterializedViewConfig(
    val reference: String,
    val mappings: MaterializedViewMappings = emptyMap()
) {

    fun apply(table: KClass<*>, expression: SqlExpression): SqlExpression {
        var sql = expression.sql

        this.mappings.forEach { (table, value) ->
            sql = sql.replace("${table.tableName()}.", value)
        }

        sql = sql.replace(table.tableName(), this.reference)

        return SqlExpression(sql, expression.fill)
    }

}

data class SearchQueryConfig(
    val table: KClass<*>,
    val printIdColumn: KProperty1<*, UUID>?,
    val faceIndexColumn: KProperty1<*, Int>?,
    val languageColumns: Array<KProperty1<*, Any>> = emptyArray(),
    val tableDependencies: Map<KClass<*>, TableDependency> = emptyMap(),
)

interface CollectionSearchQueryConfig

data class TableDependency(
    val tables: List<KClass<*>>,
    val join: (SelectQueryBuilder) -> Unit
) {
    constructor(vararg tables: KClass<*>, join: (SelectQueryBuilder) -> Unit) : this(tables = tables.toList(), join = join)
}

@Serializable
enum class SearchQueryDistinctMode(vararg val keywords: String, val key: String) {

    @SerialName("unique:cards")
    UNIQUE_CARDS(key = Strings.Query.Subject.Cards.KEY),

    @SerialName("unique:prints")
    UNIQUE_PRINTS("++", key = Strings.Query.Subject.Prints.KEY),

    @SerialName("unique:faces")
    UNIQUE_FACES(key = Strings.Query.Subject.Faces.KEY),

    @SerialName("unique:printfaces")
    UNIQUE_PRINT_FACES(key = Strings.Query.Subject.PrintFaces.KEY),

    @SerialName("unique:art")
    UNIQUE_ART(key = Strings.Query.Subject.Prints.KEY);

}