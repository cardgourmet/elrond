package dev.cowzy.cardgourmet.elrond.config.mtg

import dev.cowzy.cardgourmet.commons.catalogue.MtgFinish
import dev.cowzy.cardgourmet.commons.catalogue.promoFinishesMapping
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgBlock
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgSet
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.FormatDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ManaColorsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ReprintDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ReprintNewDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.property.mtg.*
import dev.cowzy.cardgourmet.elrond.values.DataYearMappingProvider
import dev.cowzy.cardgourmet.elrond.values.StaticValueProvider
import dev.cowzy.cardgourmet.elrond.values.mtg.*
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
import dev.cowzy.cardgourmet.tagger.tags.mtg.allMechanics
import dev.cowzy.cardgourmet.tagger.tags.mtg.allProperties
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

object StaticMtgProviders {

    val properties = StaticValueProvider(allProperties.map { it.key }.toTypedArray())
    val mechanics = StaticValueProvider(allMechanics.map { it.key }.toTypedArray())
    val finishes = StaticValueProvider((MtgFinish.values().toList() - promoFinishesMapping.values.toSet()).map { it.getSerialName() }.toSet())
    val mediums = StaticValueProvider(MtgMedium.values().map { it.getSerialName() }.toSet())
    val languages = StaticValueProvider(MtgLanguage.values().map { it.getSerialName() }.toSet())

    val manaCardinalityMappings = mapOf(
        "colorless" to (0 to SearchQueryOperator.EQUALS),
        "monocolor" to (1 to SearchQueryOperator.EQUALS),
        "bicolor" to (2 to SearchQueryOperator.EQUALS),
        "dualcolor" to (2 to SearchQueryOperator.EQUALS),
        "tricolor" to (3 to SearchQueryOperator.EQUALS),
        "quadcolor" to (4 to SearchQueryOperator.EQUALS),
        "omnicolor" to (5 to SearchQueryOperator.EQUALS),
        "multicolor" to (2 to SearchQueryOperator.GREATER_THAN_OR_EQUALS),
        "any" to (1 to SearchQueryOperator.GREATER_THAN_OR_EQUALS)
    )

    val mtgFinishMappings = mapOf(
        "foil" to "traditional_foil"
    )

    val mtgLayoutMappings = mapOf(
        "art" to "art_series",
        "modal" to "modal_dfc",
        "dft" to "double_faced_token",
        "reversible" to "reversible_card"
    )

    val mtgMediumMappings = mapOf(
        "online" to "mtgo"
    )

    val mtgPropertyMappings = mapOf(
        "alchemy" to "arena",
        "story_spotlight" to "spotlight",
        "storyspotlight" to "spotlight",
        "dfc" to "double_faced_card",
    ) + allProperties
        .filter { it.key.contains("_") }
        .associate { it.key.replace("_", "") to it.key }

    val mtgFrameEffectMappings = mapOf(
        "extended_art" to "extendedart"
    )

    val mtgLanguageMappings = MtgLanguage.values().map { language ->
        language.aliases.map { alias ->
            if (alias.contains("_")) {
                listOf(
                    alias.replace("_", "") to language.getSerialName(),
                    alias to language.getSerialName()
                )
            } else {
                listOf(alias to language.getSerialName())
            }
        }.flatten()
    }.flatten().toMap()

    val mtgMechanicsMappings = allMechanics
        .filter { it.key.contains("_") }
        .associate { it.key.replace("_", "") to it.key }

}

private val propertyKeys = Strings.Query.Property
private val mtgPropertyKeys = Strings.Query.Mtg.Property

// Numeric properties
private val manaValue = NumericColumnProperty(MtgCardFace::manaValue, propertyKey = mtgPropertyKeys.MANA_VALUE)
private val collectorNumberValue = NumericColumnProperty(MtgPrint::collectorNumberValue, propertyKey = propertyKeys.COLLECTOR_NUMBER)
private val powerValue = NumericColumnProperty(MtgCardFace::powerValue, propertyKey = mtgPropertyKeys.POWER)
private val toughnessValue = NumericColumnProperty(MtgCardFace::toughnessValue, propertyKey = mtgPropertyKeys.TOUGHNESS)
private val loyaltyValue = NumericColumnProperty(MtgCardFace::loyaltyValue, propertyKey = mtgPropertyKeys.LOYALTY)
private val defenseValue = NumericColumnProperty(MtgCardFace::defenseValue, propertyKey = mtgPropertyKeys.DEFENSE)
private val powerToughnessValue = NumericColumnProperty(MtgCardFace::powerValue, MtgCardFace::toughnessValue, propertyKey = mtgPropertyKeys.COMBINED_POWER_TOUGHNESS)
private val faceCount = NumericColumnProperty(MtgCard::faceCount, propertyKey = propertyKeys.FACE_COUNT)
private val faceNumber = NumericColumnProperty(MtgCardFace::index, offset = 1.0, propertyKey = propertyKeys.FACE_NUMBER)
private val releaseYear = YearOfDateProperty(MtgPrint::releaseDate, propertyKey = propertyKeys.RELEASE_YEAR)
private val printCount = MtgPrintCountProperty()
private val paperPrintCount = MtgPaperPrintCountProperty()
private val setCount = MtgSetCountProperty()
private val paperSetCount = MtgPaperSetCountProperty()
private val printFinishesCount = ArrayCardinalityProperty(MtgPrint::finishes, propertyKey = propertyKeys.FINISH_COUNT)
private val watermarksCount = ArrayCardinalityProperty(MtgPrint::watermarks, propertyKey = mtgPropertyKeys.WATERMARK)
private val printKeywordsCount = ArrayCardinalityProperty(MtgCard::keywords, propertyKey = propertyKeys.KEYWORD_COUNT)
private val printMechanicsCount = ArrayCardinalityProperty(MtgPrintFace::mechanicTags, propertyKey = propertyKeys.MECHANIC_COUNT)
private val printPropertyCount = ArrayCardinalityProperty(MtgPrintFace::propertyTags, propertyKey = propertyKeys.PROPERTY_COUNT)
private val printArtTagCount = ArrayCardinalityProperty(MtgPrintFace::artTags, propertyKey = propertyKeys.ART_TAGS_COUNT)
private val printFrameEffectsCount = ArrayCardinalityProperty(MtgPrint::frameEffects, propertyKey = mtgPropertyKeys.FRAME_EFFECT_COUNT)
private val legalFormatsCount = ArrayCardinalityProperty(MtgPrint::formatsLegal, propertyKey = mtgPropertyKeys.FORMATS_LEGAL_COUNT)
private val restrictedFormatsCount = ArrayCardinalityProperty(MtgPrint::formatsRestricted, propertyKey = mtgPropertyKeys.FORMATS_RESTRICTED_COUNT)
private val bannedFormatsCount = ArrayCardinalityProperty(MtgPrint::formatsBanned, propertyKey = mtgPropertyKeys.FORMATS_BANNED_COUNT)
private val printMediumsCount = ArrayCardinalityProperty(MtgPrint::mediums, propertyKey = propertyKeys.MEDIUM_COUNT)
private val printPromoTypesCount = ArrayCardinalityProperty(MtgPrint::promoTypes, propertyKey = mtgPropertyKeys.PROMO_TYPE_COUNT)
private val colorCount = ArrayCardinalityProperty(MtgCardFace::colors, StaticMtgProviders.manaCardinalityMappings, propertyKey = mtgPropertyKeys.COLOR_COUNT) // TODO: explain mappings
private val indicatorCount = ArrayCardinalityProperty(MtgCardFace::colorIndicator, StaticMtgProviders.manaCardinalityMappings, propertyKey = mtgPropertyKeys.COLOR_INDICATOR_COUNT) // TODO: explain mappings
private val identityCount = ArrayCardinalityProperty(MtgCard::colorIdentity, StaticMtgProviders.manaCardinalityMappings, propertyKey = mtgPropertyKeys.COLOR_IDENTITY_COUNT) // TODO: explain mappings
private val producesCount = ArrayCardinalityProperty(MtgCardFace::producesMana, StaticMtgProviders.manaCardinalityMappings.filter { it.key != "colorless" }, propertyKey = mtgPropertyKeys.PRODUCED_MANA_COUNT) // TODO: explain mappings
private val priceUsd = NumericColumnProperty(MtgPrintPrice::priceUsd, propertyKey = propertyKeys.PRICE_USD)
private val priceEur = NumericColumnProperty(MtgPrintPrice::priceEur, propertyKey = propertyKeys.PRICE_EUR)
private val priceTix = NumericColumnProperty(MtgPrintPrice::priceTix, propertyKey = propertyKeys.PRICE_TIX)
private val languageCount = ArrayCardinalityProperty(MtgPrint::languages, propertyKey = propertyKeys.LANGUAGE_COUNT)
private val edhrecRank = NumericColumnProperty(MtgCard::edhrecRank, propertyKey = mtgPropertyKeys.EDHREC_RANK)

// String properties
private val setName = StringColumnProperty(MtgSet::name, mappings = mapOf("plist" to "plst", "ulist" to "ulst"), descriptor = StringDescriptor(propertyKeys.SET_NAME))
private val setType = StringColumnProperty(MtgSet::type, mapContainsToEquals = true, descriptor = StringDescriptor(mtgPropertyKeys.SET_TYPE))
private val typeLine = StringColumnProperty(MtgCardFaceTranslation::typeLine, simpleColumn = MtgCardFaceTranslation::simpleTypeLine, useStrictValues = true, descriptor = StringDescriptor(mtgPropertyKeys.TYPE_LINE))
private val oracleText = MtgOracleTextProperty(MtgCardFaceTranslation::oracleText, MtgCardFaceTranslation::simpleOracleText, descriptor = StringDescriptor(propertyKeys.TEXT))
private val fullOracleText = MtgOracleTextProperty(MtgCardFaceTranslation::fullOracleText, MtgCardFaceTranslation::simpleFullOracleText, descriptor = StringDescriptor(propertyKeys.TEXT_WITH_REMINDERS))
private val flavorName = StringColumnProperty(MtgPrintFaceTranslation::flavorName, simpleColumn = MtgPrintFaceTranslation::simpleFlavorName, descriptor = StringDescriptor(mtgPropertyKeys.FLAVOR_NAME))
private val flavorText = StringColumnProperty(MtgPrintFaceTranslation::flavorText, simpleColumn = MtgPrintFaceTranslation::simpleFlavorText, descriptor = StringDescriptor(propertyKeys.FLAVOR_TEXT))
private val artist = StringColumnProperty(MtgPrint::artist, descriptor = StringDescriptor(propertyKeys.ARTIST))
private val powerDisplay = StringColumnProperty(MtgCardFace::powerDisplay, descriptor = StringDescriptor(mtgPropertyKeys.POWER))
private val toughnessDisplay = StringColumnProperty(MtgCardFace::toughnessDisplay, descriptor = StringDescriptor(mtgPropertyKeys.TOUGHNESS))
private val loyaltyDisplay = StringColumnProperty(MtgCardFace::loyaltyDisplay, descriptor = StringDescriptor(mtgPropertyKeys.LOYALTY))
private val defenseDisplay = StringColumnProperty(MtgCardFace::defenseDisplay, descriptor = StringDescriptor(mtgPropertyKeys.DEFENSE))
private val collectorNumberDisplay = StringColumnProperty(MtgPrint::collectorNumber, descriptor = StringDescriptor(propertyKeys.COLLECTOR_NUMBER))
private val blockName = StringColumnProperty(MtgBlock::name, descriptor = StringDescriptor("DUMMY")) // TODO

// Text array properties
private val printProperties = StringArrayColumnProperty(MtgPrintFace::propertyTags, valueProvider = StaticMtgProviders.properties, mappings = StaticMtgProviders.mtgPropertyMappings, descriptor = IsPresentDescriptor(propertyKeys.PROPERTY))
private val printFinishes = StringArrayColumnProperty(MtgPrint::finishes, valueProvider = StaticMtgProviders.finishes, mappings = StaticMtgProviders.mtgFinishMappings, descriptor = IsPresentDescriptor(propertyKeys.FINISH))
private val printMechanics = StringArrayColumnProperty(MtgPrintFace::mechanicTags, valueProvider = StaticMtgProviders.mechanics, mappings = StaticMtgProviders.mtgMechanicsMappings, descriptor = IsPresentDescriptor(propertyKeys.MECHANIC))
private val printArtTags = StringArrayColumnProperty(MtgPrintFace::artTags, descriptor = IsPresentDescriptor(propertyKeys.ART_TAGS))
private val printMediums = StringArrayColumnProperty(MtgPrint::mediums, valueProvider = StaticMtgProviders.mediums, mappings = StaticMtgProviders.mtgMediumMappings, descriptor = IsPresentDescriptor(propertyKeys.MEDIUM))

// Special properties
private val colors = MtgManaArrayColumnProperty(MtgCardFace::colors, descriptor = ManaColorsDescriptor(mtgPropertyKeys.MANA_COLORS, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS))
private val produces = MtgManaArrayColumnProperty(MtgCardFace::producesMana, descriptor = NumericDescriptor(mtgPropertyKeys.PRODUCED_MANA, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS))
private val indicator = MtgManaArrayColumnProperty(MtgCardFace::colorIndicator, descriptor = NumericDescriptor(mtgPropertyKeys.COLOR_INDICATOR, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS))
private val identity = MtgManaArrayColumnProperty(MtgCard::colorIdentity, mapContainsToLessThanOrEquals = true, descriptor = NumericDescriptor(mtgPropertyKeys.COLOR_IDENTITY, mapContainsTo = SearchQueryOperator.LESS_THAN_OR_EQUALS))
private val rarity = MtgRarityProperty()
private val releaseDate = DateProperty(MtgPrint::releaseDate, propertyKey = propertyKeys.RELEASE_DATE)
private val printLanguage = MtgPrintLanguageProperty(MtgPrint::languages, MtgPrintFaceTranslation::language)
private val printLanguages = MtgPrintLanguagesProperty(MtgPrint::languages)
private val manaDisplay = MtgManaDisplayProperty()
private val devotion = MtgDevotionProperty()

// Id properties
private val printId = UuidColumnProperty(MtgPrint::id, descriptor = EqualsDescriptor(propertyKeys.PRINT_ID))
private val cardId = UuidColumnProperty(MtgCard::id, descriptor = EqualsDescriptor(propertyKeys.CARD_ID))
private val cardFaceId = UuidColumnProperty(MtgCardFace::id, descriptor = EqualsDescriptor(propertyKeys.CARD_FACE_ID))
private val printFaceId = UuidColumnProperty(MtgPrintFace::id, descriptor = EqualsDescriptor(propertyKeys.PRINT_FACE_ID))
private val scryfallId = StringColumnProperty(MtgPrintIdentifier::scryfallId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.SCRYFALL_ID))
private val scryfallOracleId = StringColumnProperty(MtgPrintIdentifier::scryfallOracleId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.SCRYFALL_ORACLE_ID))
private val tcgplayerId = StringColumnProperty(MtgPrintIdentifier::tcgplayerId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.TCGPLAYER_ID))
private val cardKingdomId = StringColumnProperty(MtgPrintIdentifier::cardKingdomId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.CARDKINGDOM_ID))
private val cardmarketId = StringColumnProperty(MtgPrintIdentifier::cardmarketId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.CARDMARKET_ID))
private val mtgArenaId = StringColumnProperty(MtgPrintIdentifier::mtgArenaId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.MTGARENA_ID))
private val mtgoId = StringColumnProperty(MtgPrintIdentifier::mtgOnlineId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.MTGO_ID))
private val mtgjsonId = StringColumnProperty(MtgPrintIdentifier::mtgjsonId, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.MTGJSON_ID))
private val setId = StringColumnProperty(MtgPrint::setId, mapContainsToEquals = true, descriptor = EqualsDescriptor("DUMMY")) // TODO
private val blockId = StringColumnProperty(MtgSet::blockId, mapContainsToEquals = true, descriptor = EqualsDescriptor("DUMMY")) // TODO

data class MtgValueProviders(
    val names: MtgNameValueProvider,
    val layouts: MtgLayoutValueProvider,
    val types: MtgTypeValueProvider,
    val superTypes: MtgTypeValueProvider,
    val subTypes: MtgTypeValueProvider,
    val formats: MtgFormatValueProvider,
    val promoTypes: MtgPromoTypeValueProvider,
    val keywords: MtgKeywordValueProvider,
    val reprintIn: MtgReprintInValueProvider,
    val reprintNew: MtgReprintNewValueProvider,
    val setCodes: MtgSetCodeValueProvider,
    val setReleaseDates: MtgSetReleaseDateMappingProvider,
    val frameEffects: MtgFrameEffectValueProvider,
    val borders: MtgBorderValueProvider,
    val frames: MtgFrameValueProvider,
    val stamps: MtgStampValueProvider,
    val watermarks: MtgWatermarkValueProvider
)

fun createMtgDefaultFilter(providers: MtgValueProviders): QueryFilter {
    val property = MtgNameProperty(valueProvider = providers.names)
    return QueryFilter(arrayOf("name", "n"), property)
}

fun createBasicMtgSearchQueryFilters(providers: MtgValueProviders): List<QueryFilter> {
    val layout = StringColumnProperty(MtgCard::layout, valueProvider = providers.layouts, useStrictValues = true, mappings = StaticMtgProviders.mtgLayoutMappings, mapContainsToEquals = true, descriptor = EqualsDescriptor(mtgPropertyKeys.LAYOUT))

    val types = StringArrayColumnProperty(MtgCardFace::types, valueProvider = providers.types, descriptor = IsPresentDescriptor(mtgPropertyKeys.TYPE))
    val superTypes = StringArrayColumnProperty(MtgCardFace::superTypes, valueProvider = providers.superTypes, descriptor = IsPresentDescriptor(mtgPropertyKeys.SUPER_TYPE))
    val subTypes = StringArrayColumnProperty(MtgCardFace::subTypes, valueProvider = providers.subTypes, descriptor = IsPresentDescriptor(mtgPropertyKeys.SUB_TYPE))

    val legalFormats = StringArrayColumnProperty(MtgPrint::formatsLegal, valueProvider = providers.formats, descriptor = FormatDescriptor(FormatDescriptor.Type.LEGAL), key = "legal_formats")
    val restrictedFormats = StringArrayColumnProperty(MtgPrint::formatsRestricted, valueProvider = providers.formats, descriptor = FormatDescriptor(FormatDescriptor.Type.RESTRICTED), key = "restricted_formats")
    val bannedFormats = StringArrayColumnProperty(MtgPrint::formatsBanned, valueProvider = providers.formats, descriptor = FormatDescriptor(FormatDescriptor.Type.BANNED), key = "banned_formats")

    val printKeywords = StringArrayColumnProperty(MtgCard::keywords, valueProvider = providers.keywords, descriptor = IsPresentDescriptor(propertyKeys.KEYWORD))
    val printPromoTypes = StringArrayColumnProperty(MtgPrint::promoTypes, valueProvider = providers.promoTypes, descriptor = IsPresentDescriptor(mtgPropertyKeys.PROMO_TYPE))

    val reprintNew = StringArrayColumnProperty(MtgPrint::reprintNew, valueProvider = providers.reprintNew, descriptor = ReprintNewDescriptor(), key = "reprint_new")
    val reprintIn = StringArrayColumnProperty(MtgCard::reprintIn, valueProvider = providers.reprintIn, descriptor = ReprintDescriptor(ReprintDescriptor.Mode.REPRINT_IN), key = "reprint_in")

    val setCode = StringColumnProperty(MtgSet::code, valueProvider = providers.setCodes, mappings = mapOf("plist" to "plst", "ulist" to "ulst"), descriptor = StringDescriptor(propertyKeys.SET_CODE))

    val printReleaseDateBySet = DateByMappingProperty(MtgPrint::releaseDate, mappingProvider = providers.setReleaseDates, propertyKey = propertyKeys.RELEASE_DATE)
    val printReleaseYearBySet = YearByMappingProperty(MtgPrint::releaseDate, mappingProvider = DataYearMappingProvider(providers.setReleaseDates), propertyKey = propertyKeys.RELEASE_YEAR)

    val frameEffects = StringArrayColumnProperty(MtgPrint::frameEffects, valueProvider = providers.frameEffects, mappings = StaticMtgProviders.mtgFrameEffectMappings, descriptor = IsPresentDescriptor(mtgPropertyKeys.FRAME_EFFECT))
    val frame = StringColumnProperty(MtgPrint::frame, mapContainsToEquals = true, valueProvider = providers.frames, descriptor = StringDescriptor(mtgPropertyKeys.FRAME))
    val stamp = StringColumnProperty(MtgPrint::stamp, mapContainsToEquals = true, valueProvider = providers.stamps, descriptor = StringDescriptor(mtgPropertyKeys.STAMP))
    val border = StringColumnProperty(MtgPrint::border, mapContainsToEquals = true, valueProvider = providers.borders, descriptor = StringDescriptor(mtgPropertyKeys.BORDER))
    val watermarks = StringArrayColumnProperty(MtgPrint::watermarks, valueProvider = providers.watermarks, descriptor = IsPresentDescriptor(mtgPropertyKeys.WATERMARK))

    return listOf(
        QueryFilter(arrayOf("cmc", "mv", "manavalue", "manacost"), manaValue),
        QueryFilter(arrayOf("display", "mana", "m", "manadisplay"), manaDisplay),
        QueryFilter(arrayOf("devotion"), devotion),
        QueryFilter(arrayOf("color", "colors", "c"), colorCount, colors),
        QueryFilter(arrayOf("produces"), producesCount, produces),
        QueryFilter(arrayOf("id", "identity", "coloridentity", "ci", "commander"), identityCount, identity, ignoreReferenceKeywords = arrayOf("commander")),
        QueryFilter(arrayOf("indicator", "indicatorcolors"), indicatorCount, indicator),
        QueryFilter(arrayOf("power", "pow"), powerValue, powerDisplay),
        QueryFilter(arrayOf("toughness", "tou"), toughnessValue, toughnessDisplay),
        QueryFilter(arrayOf("loyalty", "loy"), loyaltyValue, loyaltyDisplay),
        QueryFilter(arrayOf("defense", "def"), defenseValue, defenseDisplay),
        QueryFilter(arrayOf("pt", "powtou", "combinedpt", "heft"), powerToughnessValue),
        QueryFilter(arrayOf("faces", "facecount"), faceCount),
        QueryFilter(arrayOf("face", "facenumber"), faceNumber),
        QueryFilter(arrayOf("year", "releaseyear"), printReleaseYearBySet, releaseYear),
        QueryFilter(arrayOf("date", "releasedate"), printReleaseDateBySet, releaseDate),
        QueryFilter(arrayOf("prints", "printcount"), printCount),
        QueryFilter(arrayOf("paperprints", "paperprintcount"), paperPrintCount),
        QueryFilter(arrayOf("sets", "setcount"), setCount),
        QueryFilter(arrayOf("papersets", "papersetcount"), paperSetCount),
        QueryFilter(arrayOf("finishes", "finish"), printFinishesCount, printFinishes),
        QueryFilter(arrayOf("watermark", "watermarks", "wm"), watermarksCount, watermarks),
        QueryFilter(arrayOf("keyword", "keywords", "key"), printKeywordsCount, printKeywords),
        QueryFilter(arrayOf("mechanic", "mechanics", "function", "otag", "oracletag"), printMechanicsCount, printMechanics),
        QueryFilter(arrayOf("property", "properties"), printPropertyCount, printProperties),
        QueryFilter(arrayOf("art"), printArtTagCount, printArtTags),
        QueryFilter(arrayOf("is", "has", "tag", "tags"), layout, rarity, printFinishes, printPromoTypes, printMediums, types, superTypes, subTypes, frameEffects, printProperties),
        QueryFilter(arrayOf("not"), layout, rarity, printFinishes, printPromoTypes, printMediums, types, superTypes, subTypes, frameEffects, printProperties, inverted = true),
        QueryFilter(arrayOf("legal", "legalformats", "legalin", "format"), legalFormatsCount, legalFormats),
        QueryFilter(arrayOf("restricted", "restrictedformats", "restrictedin"), restrictedFormatsCount, restrictedFormats),
        QueryFilter(arrayOf("banned", "bannedformats", "bannedin"), bannedFormatsCount, bannedFormats),
        QueryFilter(arrayOf("medium", "mediums", "game", "games"), printMediumsCount, printMediums),
        QueryFilter(arrayOf("new"), reprintNew),
        QueryFilter(arrayOf("in"), reprintIn),
        QueryFilter(arrayOf("promo", "promotypes", "promotype"), printPromoTypesCount, printPromoTypes),
        QueryFilter(arrayOf("set", "setcode", "s", "e", "edition"), setId, setCode, setName),
        QueryFilter(arrayOf("setid"), setId),
        QueryFilter(arrayOf("setname"), setName),
        QueryFilter(arrayOf("settype"), setType),
        QueryFilter(arrayOf("layout"), layout),
        QueryFilter(arrayOf("typeline", "type", "types", "t"), types, superTypes, subTypes, typeLine),
        QueryFilter(arrayOf("basetype", "basetypes"), types),
        QueryFilter(arrayOf("supertype", "supertypes"), superTypes),
        QueryFilter(arrayOf("subtype", "subtypes"), subTypes),
        QueryFilter(arrayOf("oracle", "oracletext", "o"), oracleText),
        QueryFilter(arrayOf("fulloracle", "fulloracletext", "fo"), fullOracleText),
        QueryFilter(arrayOf("flavorname", "fn"), flavorName),
        QueryFilter(arrayOf("flavor", "flavortext", "ft"), flavorText),
        QueryFilter(arrayOf("frame"), frameEffects, frame),
        QueryFilter(arrayOf("frameeffect", "frameeffects"), printFrameEffectsCount, frameEffects),
        QueryFilter(arrayOf("stamp"), stamp),
        QueryFilter(arrayOf("border"), border),
        QueryFilter(arrayOf("artist", "artists", "illustrator", "illustrators"), artist),
        QueryFilter(arrayOf("cn", "number", "collectornumber"), collectorNumberValue, collectorNumberDisplay),
        QueryFilter(arrayOf("rarity", "r"), rarity),
        QueryFilter(arrayOf("lang", "language", "printlang", "printlanguage"), printLanguage),
        QueryFilter(arrayOf("langs", "languages", "printlangs", "printlanguages"), languageCount, printLanguages),
        QueryFilter(arrayOf("eur"), priceEur),
        QueryFilter(arrayOf("usd"), priceUsd),
        QueryFilter(arrayOf("tix"), priceTix),
        QueryFilter(arrayOf("print", "printid"), printId),
        QueryFilter(arrayOf("card", "cardid", "oracleid"), cardId),
        QueryFilter(arrayOf("face", "faceid"), cardFaceId),
        QueryFilter(arrayOf("printface", "printfaceid"), printFaceId),
        QueryFilter(arrayOf("scryfall", "scryfallid"), scryfallId),
        QueryFilter(arrayOf("scryfalloracle", "scryfalloracleid", "oscryfall", "oscryfallid"), scryfallOracleId),
        QueryFilter(arrayOf("tcgplayer", "tcgplayerid"), tcgplayerId),
        QueryFilter(arrayOf("cardkingdom", "cardkingdomid"), cardKingdomId),
        QueryFilter(arrayOf("cardmarket", "cardmarketid"), cardmarketId),
        QueryFilter(arrayOf("mtgarena", "mtgarenaid", "arenaid"), mtgArenaId),
        QueryFilter(arrayOf("mtgo", "mtgoid"), mtgoId),
        QueryFilter(arrayOf("mtgjson", "mtgjsonid"), mtgjsonId),
        QueryFilter(arrayOf("edhrec", "edhrecrank"), edhrecRank),
        QueryFilter(arrayOf("block", "era"), blockId, blockName),
        QueryFilter(arrayOf("blockid", "eraid"), blockId),
        QueryFilter(arrayOf("blockname", "eraname"), blockName),
    )
}

private val tableDependencies = mapOf(
    MtgCard::class to TableDependency(MtgPrint::class) { builder ->
        builder.innerJoin(MtgCard::class) { it.whereColumn(MtgCard::id, MtgPrint::cardId) }
    },
    MtgCardFace::class to TableDependency(MtgCard::class) { builder ->
        builder.innerJoin(MtgCardFace::class) { it.whereColumn(MtgCardFace::cardId, MtgCard::id) }
    },
    MtgCardFaceTranslation::class to TableDependency(MtgCardFace::class) { builder ->
        builder.innerJoin(MtgCardFaceTranslation::class) {
            it.whereColumn(MtgCardFaceTranslation::cardFaceId, MtgCardFace::id)
        }
    },
    MtgPrintFace::class to TableDependency(MtgCardFace::class) { builder ->
        builder.innerJoin(MtgPrintFace::class) {
            it
                .whereColumn(MtgPrintFace::cardFaceId, MtgCardFace::id)
                .whereColumn(MtgPrintFace::printId, MtgPrint::id)
        }
    },
    MtgPrintFaceTranslation::class to TableDependency(MtgPrintFace::class, MtgCardFaceTranslation::class) { builder ->
        builder.leftJoin(MtgPrintFaceTranslation::class) {
            it
                .whereColumn(MtgPrintFaceTranslation::printFaceId, MtgPrintFace::id)
//                .whereColumn(MtgPrintFaceTranslation::language, MtgCardFaceTranslation::language)
        }
    },
    MtgPrintIdentifier::class to TableDependency(MtgPrint::class) { builder ->
        builder.leftJoin(MtgPrintIdentifier::class) { it.whereColumn(MtgPrintIdentifier::printId, MtgPrint::id) }
    },
    MtgPrintPrice::class to TableDependency(MtgPrint::class) { builder ->
        builder.leftJoin(MtgPrintPrice::class) { it.whereColumn(MtgPrintPrice::printId, MtgPrint::id) }
    },
    MtgSet::class to TableDependency(MtgPrint::class) { builder ->
        builder.innerJoin(MtgSet::class) { it.whereColumn(MtgSet::id, MtgPrint::setId) }
    },
    MtgBlock::class to TableDependency(MtgSet::class) { builder ->
        builder.leftJoin(MtgBlock::class) { it.whereColumn(MtgBlock::id, MtgSet::blockId) }
    },
    CardPrice::class to TableDependency(MtgPrint::class) { builder ->
        builder.leftJoin(CardPrice::class) {
            it
                .whereColumn(MtgPrint::id, CardPrice::cardId)
                .where(CardPrice::game, GameType.MAGIC_THE_GATHERING)
        }
    },
    CardImage::class to TableDependency(MtgPrintFaceTranslation::class) { builder ->
        builder.leftJoin(CardImage::class) { it.whereColumn(CardImage::printTranslationId, MtgPrintFaceTranslation::id) }
    }
)

val mtgBasicSearchQueryConfig = SearchQueryConfig(
    table = MtgPrint::class,
    printIdColumn = MtgPrint::id,
    faceIndexColumn = MtgCardFace::index,
    languageColumns = arrayOf(MtgPrintFaceTranslation::language, MtgCardFaceTranslation::language),
    tableDependencies = tableDependencies,
)
