package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgLanguage
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.AvailableInDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import kotlin.reflect.KProperty1

class MtgPrintLanguagesProperty(
    private val languagesColumn: KProperty1<*, *>,
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<MtgLanguage>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(languagesColumn.table()),
    descriptor = AvailableInDescriptor(Strings.Query.Property.PRINT),
    key = "language"
) {

    override val valueDefinition = createMtgPrintLanguageValueDefinition(valueProviderPool)

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: MtgLanguage
    ) {
        builder.whereRaw(languagesColumn, "@>", "ARRAY[${languagesColumn.placeholder()}]::text[]") { stmt, index ->
            stmt.setString(index.getAndIncrement(), value.getSerialName())
        }
    }

}