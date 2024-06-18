package dev.cowzy.cardgourmet.elrond.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.catalogue.dlc.DlcInkType
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.dlc.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcFranchise
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.QueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.AvailableInDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.values.autoValues
import dev.cowzy.kuery.column.transformer.LocalDateColumnTransformer
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin
import dev.cowzy.kuery.query.selectBuilder
import java.sql.Connection
import java.time.format.DateTimeFormatter

val dlcSetCodeMappings = mapOf("promo" to "P1", "tfc" to "1", "rof" to "2", "ink" to "3", "urs" to "4")

private val propertyKeys = Strings.Query.Property
private val dlcPropertyKeys = Strings.Query.Dlc.Property

private val getSetReleaseDates = { connection: Connection ->
    DlcSet::class.selectBuilder()
        .distinctOn(DlcSet::code)
        .select(DlcSet::code)
        .select(DlcSet::releaseDate)
        .get(connection) { row, index ->
            row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)!!
        }.toMap()
}

private val getSetMarketReleaseDates = { connection: Connection ->
    DlcSet::class.selectBuilder()
        .distinctOn(DlcSet::code)
        .select(DlcSet::code)
        .select(DlcSet::marketReleaseDate)
        .get(connection) { row, index ->
            row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)!!
        }.toMap()
}

fun SearchQueryConfigBuilder.configureBasicDlcFilters() {
    filter("name", "n") {
        simpleString(DlcCardTranslation::name, DlcCardTranslation::simpleName, propertyKeys.NAME) {
            autoValues(DlcCardTranslation::name)
        }
    }

    filter("cost") {
        numeric(DlcCard::cost, dlcPropertyKeys.COST)
    }

    filter("ink", "inktype", "i", "color", "c", "id", "identity") {
        numeric(DlcCard::cost, dlcPropertyKeys.COST)
        enum<DlcInkType>(DlcCard::inkType, dlcPropertyKeys.INK_TYPE)
    }

    filter("strength", "power", "pow", "str") {
        numeric(DlcCard::strength, dlcPropertyKeys.STRENGTH)
    }

    filter("willpower", "will", "wp") {
        numeric(DlcCard::willpower, dlcPropertyKeys.WILLPOWER)
    }

    filter("move", "movecost", "movement") {
        numeric(DlcCard::moveCost, dlcPropertyKeys.MOVE_COST)
    }

    filter("lore", "lorevalue") {
        numeric(DlcCard::loreValue, dlcPropertyKeys.LORE)
    }

    filter("lang", "language", "printlang", "printlanguage") {
        enum<DlcLanguage>(DlcPrintTranslation::language, AvailableInDescriptor(propertyKeys.PRINT), "print_language", display = { value, i18n, locale ->
            i18n.translate(locale, "${Strings.Query.Dlc.Language.KEY}.${value.getSerialName()}")
        })
    }

    filter("cardlang", "cardlanguage") {
        enum<DlcLanguage>(DlcCardTranslation::language, AvailableInDescriptor(propertyKeys.CARD), "card_language", display = { value, i18n, locale ->
            i18n.translate(locale, "${Strings.Query.Dlc.Language.KEY}.${value.getSerialName()}")
        })
    }

    filter("keyword", "keywords", "key") {
        stringArrayAndCardinality(DlcCard::keywords, propertyKeys.KEYWORD_COUNT, propertyKeys.KEYWORD)
    }

    filter("class", "classes", "classification", "classifications", "trait", "traits", "subtype", "subtypes") {
        stringArrayAndCardinality(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION_COUNT, dlcPropertyKeys.CLASSIFICATION)
    }

    filter("type", "t", "types") {
        string(DlcCard::type, dlcPropertyKeys.TYPE) {
            strict(true)
            autoValues(DlcCard::type, autoAlias = true)
        }
        stringArray(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION)
    }

    filter("supertype") {
        string(DlcCard::type, dlcPropertyKeys.TYPE) {
            strict(true)
            autoValues(DlcCard::type, autoAlias = true)
        }
    }

    filter("date", "releasedate") {
        date(DlcSet::releaseDate, propertyKey = propertyKeys.RELEASE_DATE) {
            values(getSetReleaseDates, { it.format(DateTimeFormatter.ISO_DATE) }, "set_code", merge = false)
        }
    }

    filter("year", "releaseyear") {
        year(DlcSet::releaseDate, propertyKey = propertyKeys.RELEASE_YEAR) {
            values(getSetReleaseDates, { it.year }, "set_code", merge = false)
        }
    }

    filter("marketdate", "marketreleasedate") {
        date(DlcSet::marketReleaseDate, propertyKey = dlcPropertyKeys.MARKET_RELEASE_DATE) {
            values(getSetMarketReleaseDates, { it.format(DateTimeFormatter.ISO_DATE) }, "set_code", merge = false)
        }
    }

    filter("marketyear", "marketreleaseyear") {
        year(DlcSet::marketReleaseDate, propertyKey = dlcPropertyKeys.MARKET_RELEASE_YEAR) {
            values(getSetMarketReleaseDates, { it.year }, "set_code", merge = false)
        }
    }

    filter("artist", "illustrator") {
        string(DlcPrint::artist, propertyKeys.ARTIST)
    }

    filter("set", "s", "e", "edition", "expansion") {
        uuid(DlcSet::id, propertyKeys.SET_ID)
        string(DlcSet::code, propertyKeys.SET_CODE) {
            strict(true)
            autoValues(DlcSet::code)
            values(dlcSetCodeMappings, "set_code")
        }
    }

    filter("setid") { uuid(DlcSet::id, propertyKeys.SET_ID) }
    filter("setname") {
        string(DlcSet::name, propertyKeys.SET_NAME) { autoValues(DlcSet::name) }
    }

    filter("setcode") {
        string(DlcSet::code, propertyKeys.SET_CODE) {
            strict(true)
            autoValues(DlcSet::code)
            values(dlcSetCodeMappings, "set_code")
        }
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(DlcPrint::collectorNumberValue, DlcPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("title") {
        simpleString(DlcCardTranslation::title, DlcCardTranslation::simpleTitle, dlcPropertyKeys.TITLE) {
            autoValues(DlcCardTranslation::title)
        }
    }

    filter("text", "description", "abilities", "actions", "oracle", "oracletext", "o") {
        simpleString(DlcCardTranslation::text, DlcCardTranslation::simpleText, propertyKeys.TEXT)
    }

    filter("ability", "action", "actionname", "abilityname") {
        property(StringRegexProperty(
            DlcCardTranslation::text,
            { value, operator ->
                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[\"[^\"]*$value[^\"]*\"]"
                    SearchQueryOperator.EQUALS -> "\\[\"$value\"]"
                    else -> value
                }
            },
            propertyKey = dlcPropertyKeys.ABILITY_NAME
        ))
    }

    filter("fulltext", "fulldescription", "fulloracle", "fulloracletext", "fo") {
        simpleString(DlcCardTranslation::fullText, DlcCardTranslation::simpleFullText, propertyKeys.TEXT_WITH_REMINDERS)
    }

    filter("flavor", "flavortext", "ft") {
        simpleString(DlcPrintTranslation::flavorText, DlcPrintTranslation::simpleFlavorText, propertyKeys.FLAVOR_TEXT)
    }

    filter("separator") {
        exactString(DlcPrint::separator, dlcPropertyKeys.SEPARATOR) {
            strict(true)
            autoValues(DlcPrint::separator, autoAlias = true)
        }
    }

    filter("franchise") {
        uuid(DlcCard::franchiseId, "franchise_id") // TODO
        string(DlcFranchise::slug, "franchise_slug") { // TODO
            strict(true)
            autoValues(DlcFranchise::slug)
        }
        string(DlcFranchise::name, "franchise_name") { autoValues(DlcFranchise::name) }
    }

    filter("is:inkwell") {
        property(StaticColumnProperty(DlcCard::inkwell, descriptor = SimplePropertyDescriptor(Strings.Query.Dlc.Comparison.IsInkwell.KEY, propertyKeys.PRINT), key = "is_inkwell"))
    }

    filter("not:inkwell") {
        inverted(true)
        property(StaticColumnProperty(DlcCard::inkwell, descriptor = SimplePropertyDescriptor(Strings.Query.Dlc.Comparison.IsInkwell.KEY, propertyKeys.PRINT), key = "is_inkwell"))
    }

    filter("print", "printid") {
        uuid(DlcPrint::id, propertyKeys.PRINT_ID)
    }

    filter("card", "cardid", "oracleid") {
        uuid(DlcCard::id, propertyKeys.PRINT_ID)
    }

    val applyTagProperties: QueryFilterBuilder.() -> Unit = {
        enum<DlcInkType>(DlcCard::inkType, dlcPropertyKeys.INK_TYPE)
        string(DlcCard::type, dlcPropertyKeys.TYPE) {
            strict(true)
            autoValues(DlcCard::type, autoAlias = true)
        }
        stringArray(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION)
    }

    filter("is", "has", "tag", "tags") {
        applyTagProperties()
    }

    filter("not") {
        inverted(true)
        applyTagProperties()
    }

    // TODO: new/in
    // TODO: rarity
    // TODO: prints/sets (reprints)
    // TODO: new/in
}

private val tableDependencies = mapOf(
    DlcCard::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcCard::class) { it.whereColumn(DlcCard::id, DlcPrint::cardId) }
    },
    DlcCardTranslation::class to TableDependency(DlcCard::class) { builder ->
        builder.innerJoin(DlcCardTranslation::class) { it.whereColumn(DlcCard::id, DlcCardTranslation::cardId) }
    },
    DlcPrintTranslation::class to TableDependency(DlcPrint::class) { builder ->
        builder.leftJoin(DlcPrintTranslation::class) { it.whereColumn(DlcPrint::id, DlcPrintTranslation::printId) }
    },
    DlcPrintIdentifier::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcPrintIdentifier::class) { it.whereColumn(DlcPrint::id, DlcPrintIdentifier::printId) }
    },
    DlcSet::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcSet::class) { it.whereColumn(DlcPrint::setId, DlcSet::id) }
    },
    DlcFranchise::class to TableDependency(DlcCard::class) { builder ->
        builder.leftJoin(DlcFranchise::class) { it.whereColumn(DlcCard::franchiseId, DlcFranchise::id) }
    },
    CardPrice::class to TableDependency(DlcPrint::class) { builder ->
        builder.leftJoin(CardPrice::class) {
            it
                .whereColumn(DlcPrint::id, CardPrice::cardId)
                .where(CardPrice::game, GameType.DISNEY_LORCANA)
        }
    },
    CardImage::class to TableDependency(DlcPrintTranslation::class) { builder ->
        builder.leftJoin(CardImage::class) { it.whereColumn(CardImage::printTranslationId, DlcPrintTranslation::id) }
    }
)

val dlcBasicSearchQueryConfig = SearchQueryConfig(
    table = DlcPrint::class,
    printIdColumn = DlcPrint::id,
    faceIndexColumn = null,
    languageColumns = arrayOf(DlcPrintTranslation::language, DlcCardTranslation::language),
    tableDependencies = tableDependencies,
)
