package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.leftJoin
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgMedium
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.createSqlAlias
import dev.cowzy.cardgourmet.elrond.property.NumericSearchQueryProperty
import kotlin.reflect.KProperty1

abstract class MtgCardCountProperty(
    private val distinctyBy: KProperty1<MtgPrint, *>,
    private val applyCondition: ((WhereQueryBuilder<*>) -> Unit)? = null,
    descriptorSubjectKey: String
) : NumericSearchQueryProperty(
    affectedTables = emptyArray(),
    descriptorSubjectKey = descriptorSubjectKey
) {

    private val innerBuilderAlias = createSqlAlias()

    override fun applyProperty(builder: SelectQueryBuilder) {
        val innerBuilder = MtgPrint::class.selectBuilder()
            .selectAs(MtgPrint::cardId, "id")
            .selectRaw("COUNT(DISTINCT ${distinctyBy.columnName()}) count")
            .apply { applyCondition?.invoke(this) }
            .groupBy(MtgPrint::cardId)

        builder.leftJoin(innerBuilder, innerBuilderAlias) {
            it.whereColumn(MtgPrint::cardId, "$innerBuilderAlias.id")
        }
    }

    override fun getRawSql() = "$innerBuilderAlias.count"

}

class MtgPrintCountProperty : MtgCardCountProperty(
    distinctyBy = MtgPrint::id,
    descriptorSubjectKey = Strings.Query.Property.PRINT_COUNT,
)

class MtgPaperPrintCountProperty : MtgCardCountProperty(
    distinctyBy = MtgPrint::id,
    applyCondition = { it.whereRaw(MtgPrint::mediums, "@>", "ARRAY['${MtgMedium.PAPER.getSerialName()}']") },
    descriptorSubjectKey = Strings.Query.Property.PRINT_COUNT_PAPER,
)

class MtgSetCountProperty : MtgCardCountProperty(
    distinctyBy = MtgPrint::setId,
    descriptorSubjectKey = Strings.Query.Property.SET_COUNT,
)

class MtgPaperSetCountProperty : MtgCardCountProperty(
    distinctyBy = MtgPrint::setId,
    applyCondition = { it.whereRaw(MtgPrint::mediums, "@>", "ARRAY['${MtgMedium.PAPER.getSerialName()}']") },
    descriptorSubjectKey = Strings.Query.Property.SET_COUNT_PAPER,
)
