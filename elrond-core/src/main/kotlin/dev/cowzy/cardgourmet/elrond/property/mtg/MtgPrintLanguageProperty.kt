package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgLanguage
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.query.orWhere
import kotlin.reflect.KProperty1

class MtgPrintLanguageProperty(
    private val languagesColumn: KProperty1<*, *>,
    private vararg val languageColumns: KProperty1<*, *>,
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<MtgLanguage>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(languagesColumn.table(), *languageColumns.map { it.table() }.toTypedArray()),
    descriptor = EqualsDescriptor(propertyKey = Strings.Query.Property.PRINT_LANGUAGE),
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

        builder.where { inner ->
            languageColumns.forEach {
                inner.orWhere(it, value.getSerialName())
            }
        }
    }

}

fun createMtgPrintLanguageValueDefinition(valueProviderPool: ValueProviderPool) = QueryValueDefinition<MtgLanguage> {
    display { language, i18n, locale ->
        i18n.translate(locale, "${Strings.Query.Mtg.Language.KEY}.${language.getSerialName()}")
    }

    provider("mtg_print_language", valueProviderPool) {
        strict(true)
        enumValues<MtgLanguage>("language", findKeywords = { it.aliases })
    }
}
