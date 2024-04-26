package dev.cowzy.cardgourmet.elrond.config.dlc

import dev.cowzy.cardgourmet.commons.catalogue.dlc.DlcInkType
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.dlc.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.values.StaticValueProvider
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin
import java.time.LocalDate

object StaticDlcProviders {

    val finishes = StaticValueProvider(setOf("nonfoil", "foil"))
    val mediums = StaticValueProvider(setOf("paper"))
    val languages = StaticValueProvider(DlcLanguage.values().map { it.getSerialName() }.toSet())
    val inkTypes = StaticValueProvider(DlcInkType.values().map { it.getSerialName() }.toSet())

    val dlcLanguageMappings = DlcLanguage.values().associate { language ->
        language.name to language.getSerialName()
    }

}

private val propertyKeys = Strings.Query.Property
private val dlcPropertyKeys = Strings.Query.Dlc.Property

// Numeric properties
private val inkCost = NumericColumnProperty(DlcCard::cost, propertyKey = dlcPropertyKeys.COST)
private val strength = NumericColumnProperty(DlcCard::strength, propertyKey = dlcPropertyKeys.STRENGTH)
private val willpower = NumericColumnProperty(DlcCard::willpower, propertyKey = dlcPropertyKeys.WILLPOWER)
private val moveCost = NumericColumnProperty(DlcCard::moveCost, propertyKey = dlcPropertyKeys.MOVE_COST)
private val loreValue = NumericColumnProperty(DlcCard::loreValue, propertyKey = dlcPropertyKeys.LORE)
private val classificationCount = ArrayCardinalityProperty(DlcCard::classifications, propertyKey = dlcPropertyKeys.CLASSIFICATION_COUNT)
private val keywordCount = ArrayCardinalityProperty(DlcCard::keywords, propertyKey = propertyKeys.KEYWORD_COUNT)
private val collectorNumberValue = NumericColumnProperty(DlcPrint::collectorNumberValue, propertyKey = propertyKeys.COLLECTOR_NUMBER)
private val releaseYear = YearOfDateProperty(DlcSet::releaseDate, descriptorSubjectKey = propertyKeys.RELEASE_YEAR)
private val marketReleaseYear = YearOfDateProperty(DlcSet::marketReleaseDate, descriptorSubjectKey = dlcPropertyKeys.MARKET_RELEASE_YEAR)
// TODO: ability count
// TODO: reprint count
// TODO: set count

// String properties
private val inkType = StringColumnProperty(DlcCard::inkType, valueProvider = StaticDlcProviders.inkTypes, descriptor = StringDescriptor(dlcPropertyKeys.INK_TYPE))
private val franchise = StringColumnProperty(DlcCard::franchise, descriptor = StringDescriptor(dlcPropertyKeys.FRANCHISE))
private val artist = StringColumnProperty(DlcPrint::artist, descriptor = StringDescriptor(propertyKeys.ARTIST))
private val collectorNumber = StringColumnProperty(DlcPrint::collectorNumber, descriptor = StringDescriptor(propertyKeys.COLLECTOR_NUMBER))
private val setName = StringColumnProperty(DlcSet::name, descriptor = StringDescriptor(propertyKeys.SET_NAME))
private val name = StringColumnProperty(DlcCardTranslation::name, simpleColumn = DlcCardTranslation::simpleName, descriptor = StringDescriptor(propertyKeys.NAME))
private val title = StringColumnProperty(DlcCardTranslation::title, simpleColumn = DlcCardTranslation::simpleTitle, descriptor = StringDescriptor(dlcPropertyKeys.TITLE))
private val text = StringColumnProperty(DlcCardTranslation::text, simpleColumn = DlcCardTranslation::simpleText, descriptor = StringDescriptor(propertyKeys.TEXT))
private val fullText = StringColumnProperty(DlcCardTranslation::fullText, simpleColumn = DlcCardTranslation::simpleFullText, descriptor = StringDescriptor(propertyKeys.TEXT_WITH_REMINDERS))
private val flavorText = StringColumnProperty(DlcPrintTranslation::flavorText, simpleColumn = DlcPrintTranslation::simpleFlavorText, descriptor = StringDescriptor(propertyKeys.FLAVOR_TEXT))

// Date properties
private val releaseDate = DateProperty(DlcSet::releaseDate, propertyKey = propertyKeys.RELEASE_DATE)
private val marketReleaseDate = DateProperty(DlcSet::marketReleaseDate, propertyKey = dlcPropertyKeys.MARKET_RELEASE_DATE)

// Misc properties
private val inkwell = StaticColumnProperty(DlcCard::inkwell, descriptor = SimplePropertyDescriptor(Strings.Query.Dlc.Comparison.IsInkwell.KEY, propertyKeys.PRINT))
private val printId = UuidColumnProperty(DlcPrint::id, descriptor = EqualsDescriptor(propertyKeys.PRINT_ID))
private val cardId = UuidColumnProperty(DlcCard::id, descriptor = EqualsDescriptor(propertyKeys.PRINT_ID))

val dlcNameFilter = QueryFilter(arrayOf("name", "n"), name)

data class DlcValueProviders(
    val separator: ValueProvider<String>,
    val setCode: ValueProvider<String>,
    val setReleaseDates: ValueProvider<Pair<String, LocalDate>>,
    val type: ValueProvider<String>,
    val classifications: ValueProvider<String>,
    val keywords: ValueProvider<String>,
)

fun createBasicDlcSearchQueryFilters(providers: DlcValueProviders): List<QueryFilter> {
    val type = StringColumnProperty(DlcCard::type, valueProvider = providers.type, descriptor = EqualsDescriptor(dlcPropertyKeys.TYPE))
    val separator = StringColumnProperty(DlcPrint::separator, valueProvider = providers.separator, descriptor = EqualsDescriptor(dlcPropertyKeys.SEPARATOR))
    val setCode = StringColumnProperty(DlcSet::code, mappings = mapOf("promo" to "P1", "tfc" to "1", "rof" to "2", "ink" to "3", "urs" to "4"), valueProvider = providers.setCode, descriptor = EqualsDescriptor(propertyKeys.SET_CODE))
    val classifications = StringArrayColumnProperty(DlcCard::classifications, valueProvider = providers.classifications, descriptor = IsPresentDescriptor(dlcPropertyKeys.CLASSIFICATIONS))
    val keywords = StringArrayColumnProperty(DlcCard::keywords, valueProvider = providers.keywords, descriptor = IsPresentDescriptor(propertyKeys.KEYWORD))

    val releaseDateBySet = DateByMappingProperty(DlcSet::releaseDate, valueProvider = providers.setCode, mappingProvider = providers.setReleaseDates, propertyKey = propertyKeys.RELEASE_DATE)
    val releaseYearBySet = YearByMappingProperty(DlcSet::releaseDate, valueProvider = providers.setCode, mappingProvider = providers.setReleaseDates, propertyKey = propertyKeys.RELEASE_YEAR)

    val marketReleaseDateBySet = DateByMappingProperty(DlcSet::marketReleaseDate, valueProvider = providers.setCode, mappingProvider = providers.setReleaseDates, propertyKey = dlcPropertyKeys.MARKET_RELEASE_DATE)
    val marketReleaseYearBySet = YearByMappingProperty(DlcSet::marketReleaseDate, valueProvider = providers.setCode, mappingProvider = providers.setReleaseDates, propertyKey = dlcPropertyKeys.MARKET_RELEASE_YEAR)

    return listOf(
        dlcNameFilter,
        QueryFilter(arrayOf("cost"), inkCost),
        QueryFilter(arrayOf("ink", "inktype", "i","color", "c", "id", "identity"), inkCost, inkType),
        QueryFilter(arrayOf("strength", "power", "pow", "str"), strength),
        QueryFilter(arrayOf("willpower", "will", "wp"), willpower),
        QueryFilter(arrayOf("move", "movecost", "movement"), moveCost),
        QueryFilter(arrayOf("lore", "lorevalue"), loreValue),
        QueryFilter(arrayOf("keyword", "keywords", "key"), keywordCount, keywords),
        QueryFilter(arrayOf("class", "classes", "classification", "classifications", "trait", "traits", "subtype", "subtypes"), classificationCount, classifications),
        QueryFilter(arrayOf("type", "t", "types"), type, classifications),
        QueryFilter(arrayOf("supertype"), type),
        QueryFilter(arrayOf("date", "releasedate"), releaseDateBySet, releaseDate),
        QueryFilter(arrayOf("year", "releaseyear"), releaseYearBySet, releaseYear),
        QueryFilter(arrayOf("marketyear", "marketreleaseyear"), marketReleaseDateBySet, marketReleaseDate),
        QueryFilter(arrayOf("marketdate", "marketreleasedate"), marketReleaseYearBySet, marketReleaseYear),
        QueryFilter(arrayOf("artist", "illustrator"), artist),
        QueryFilter(arrayOf("set", "setcode", "s", "e", "edition"), setCode, setName),
        QueryFilter(arrayOf("cn", "number", "collectornumber"), collectorNumber, collectorNumberValue),
        QueryFilter(arrayOf("title"), title),
        QueryFilter(arrayOf("text", "description", "ability", "abilities", "action", "actions", "oracle", "oracletext", "o"), text),
        QueryFilter(arrayOf("fulltext", "fulldescription", "fulloracle", "fulloracletext", "fo"), fullText),
        QueryFilter(arrayOf("flavor", "flavortext", "ft"), flavorText),
        QueryFilter(arrayOf("separator"), separator),
        QueryFilter(arrayOf("franchise"), franchise),
        QueryFilter(arrayOf("is:inkwell"), inkwell),
        QueryFilter(arrayOf("not:inkwell"), inkwell, inverted = true),
        QueryFilter(arrayOf("tag", "tags", "is", "has"), type, classifications), // TODO: print properties
        QueryFilter(arrayOf("not"), type, classifications, inverted = true), // TODO: print properties
        QueryFilter(arrayOf("print", "printid"), printId),
        QueryFilter(arrayOf("card", "cardid", "oracleid"), cardId),
        // TODO: lang/langs
        // TODO: new/in
        // TODO: is:promo/not:promo
        // TODO: rarity
    )
}

private val tableDependencies = mapOf(
    DlcCard::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcCard::class) { it.whereColumn(DlcCard::id, DlcPrint::cardId) }
    },
    DlcCardTranslation::class to TableDependency(DlcCard::class) { builder ->
        builder.innerJoin(DlcCardTranslation::class) { it.whereColumn(DlcCard::id, DlcCardTranslation::cardId) }
    },
    DlcPrintTranslation::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcPrintTranslation::class) { it.whereColumn(DlcPrint::id, DlcPrintTranslation::printId) }
    },
    DlcPrintIdentifier::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcPrintIdentifier::class) { it.whereColumn(DlcPrint::id, DlcPrintIdentifier::printId) }
    },
    DlcSet::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcSet::class) { it.whereColumn(DlcPrint::setId, DlcSet::id) }
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
