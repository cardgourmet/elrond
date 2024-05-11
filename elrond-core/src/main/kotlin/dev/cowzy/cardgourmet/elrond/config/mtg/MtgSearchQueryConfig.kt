package dev.cowzy.cardgourmet.elrond.config.mtg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgBlock
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgSet
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.QueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.FormatDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ManaColorsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ReprintDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.mtg.ReprintNewDescriptor
import dev.cowzy.cardgourmet.elrond.property.mtg.*
import dev.cowzy.cardgourmet.elrond.values.mtg.*
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

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

val mtgLayoutMappings = mapOf(
    "art" to "art_series",
    "modal" to "modal_dfc",
    "dft" to "double_faced_token",
    "reversible" to "reversible_card"
)

val mtgPropertyMappings = mapOf(
    "alchemy" to "arena",
    "story_spotlight" to "spotlight",
    "storyspotlight" to "spotlight",
    "dfc" to "double_faced_card",
)

val mtgMediumMappings = mapOf("online" to "mtgo")
val mtgFinishMappings = mapOf("foil" to "traditional_foil")
val mtgSetCodeMappings = mapOf("plist" to "plst", "ulist" to "ulst")

private val propertyKeys = Strings.Query.Property
private val mtgPropertyKeys = Strings.Query.Mtg.Property

fun SearchQueryConfigBuilder.configureBasicMtgFilters() {
    val nameValueProvider = valueProviderPool.getOrPut("mtg_name") { MtgNameValueProvider(it) }
    val setReleaseDates = valueProviderPool.getOrPut("mtg_set_release_dates") { MtgSetReleaseDateMappingProvider(it, mtgSetCodeMappings) }
    val formatProvider = valueProviderPool.getAutoStringArrayProvider(MtgPrint::formatsLegal, MtgPrint::formatsRestricted, MtgPrint::formatsBanned)

    filter("name", "n") {
        property(MtgNameProperty(nameValueProvider))
    }

    filter("cmc", "mv", "manavalue", "manacost") { numeric(MtgCardFace::manaValue, mtgPropertyKeys.MANA_VALUE) }
    filter("display", "mana", "m", "manadisplay") { property(MtgManaDisplayProperty()) }
    filter("devotion") { property(MtgDevotionProperty()) }

    filter("color", "colors", "c") {
        cardinality(MtgCardFace::colors, mtgPropertyKeys.COLOR_COUNT, manaCardinalityMappings)
        property(MtgManaArrayColumnProperty(MtgCardFace::colors, descriptor = ManaColorsDescriptor(mtgPropertyKeys.MANA_COLORS, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS)))
    }

    filter("produces") {
        cardinality(MtgCardFace::producesMana, mtgPropertyKeys.PRODUCED_MANA_COUNT, manaCardinalityMappings)
        property(MtgManaArrayColumnProperty(MtgCardFace::producesMana, descriptor = NumericDescriptor(mtgPropertyKeys.PRODUCED_MANA, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS)))
    }

    filter("id", "identity", "coloridentity", "ci", "commander") {
        ignoreReference("commander")
        cardinality(MtgCard::colorIdentity, mtgPropertyKeys.COLOR_IDENTITY_COUNT, manaCardinalityMappings)
        property(MtgManaArrayColumnProperty(MtgCard::colorIdentity, true, NumericDescriptor(mtgPropertyKeys.COLOR_IDENTITY, mapContainsTo = SearchQueryOperator.LESS_THAN_OR_EQUALS)))
    }

    filter("indicator", "indicatorcolors") {
        cardinality(MtgCardFace::colorIndicator, mtgPropertyKeys.COLOR_INDICATOR_COUNT, manaCardinalityMappings)
        property(MtgManaArrayColumnProperty(MtgCardFace::colorIndicator, descriptor = NumericDescriptor(mtgPropertyKeys.COLOR_INDICATOR, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS)))
    }

    filter("power", "pow") {
        numericAndString(MtgCardFace::powerValue, MtgCardFace::powerDisplay, mtgPropertyKeys.POWER)
    }

    filter("toughness", "tou") {
        numericAndString(MtgCardFace::toughnessValue, MtgCardFace::toughnessDisplay, mtgPropertyKeys.TOUGHNESS)
    }

    filter("loyalty", "loy") {
        numericAndString(MtgCardFace::loyaltyValue, MtgCardFace::loyaltyDisplay, mtgPropertyKeys.LOYALTY)
    }

    filter("defense", "def") {
        numericAndString(MtgCardFace::defenseValue, MtgCardFace::defenseDisplay, mtgPropertyKeys.DEFENSE)
    }

    filter("pt", "powtou", "combinedpt", "heft") {
        numeric(MtgCardFace::powerValue, MtgCardFace::toughnessValue, propertyKey = mtgPropertyKeys.COMBINED_POWER_TOUGHNESS)
    }

    filter("faces", "facecount") { numeric(MtgCard::faceCount, propertyKeys.FACE_COUNT) }
    filter("face", "facenumber") { numeric(MtgCardFace::index, propertyKeys.FACE_NUMBER, 1.0) }

    filter("year", "releaseyear") {
        yearByMapping(MtgSet::releaseDate, setReleaseDates, propertyKeys.RELEASE_YEAR)
        year(MtgPrint::releaseDate, propertyKeys.RELEASE_YEAR)
    }

    filter("date", "releasedate") {
        dateByMapping(MtgPrint::releaseDate, setReleaseDates, propertyKeys.RELEASE_DATE)
        date(MtgPrint::releaseDate, propertyKeys.RELEASE_DATE)
    }

    filter("prints", "printcount") { property(MtgPrintCountProperty()) }
    filter("paperprints", "paperprintcount") { property(MtgPaperPrintCountProperty()) }
    filter("sets", "setcount") { property(MtgSetCountProperty()) }
    filter("papersets", "papersetcount") { property(MtgPaperSetCountProperty()) }

    filter("finishes", "finish") {
        stringArrayAndCardinality(MtgPrint::finishes, propertyKeys.FINISH_COUNT, propertyKeys.FINISH) { autoMappings(mtgFinishMappings) }
    }

    filter("watermark", "watermarks", "wm") {
        stringArrayAndCardinality(MtgPrint::watermarks, mtgPropertyKeys.WATERMARK_COUNT, mtgPropertyKeys.WATERMARK)
    }

    filter("keyword", "keywords", "key") {
        stringArrayAndCardinality(MtgCard::keywords, propertyKeys.KEYWORD_COUNT, propertyKeys.KEYWORD)
    }

    filter("mechanic", "mechanics", "function", "otag", "oracletag") {
        stringArrayAndCardinality(MtgPrintFace::mechanicTags, propertyKeys.MECHANIC_COUNT, propertyKeys.MECHANIC) { autoMappings() }
    }

    filter("property", "properties") {
        stringArrayAndCardinality(MtgPrintFace::propertyTags, propertyKeys.PROPERTY_COUNT, propertyKeys.PROPERTY) { autoMappings(mtgPropertyMappings) }
    }

    filter("art") {
        stringArrayAndCardinality(MtgPrintFace::artTags, propertyKeys.ART_TAGS_COUNT, propertyKeys.ART_TAGS)
    }

    val applyTagProperties: QueryFilterBuilder.() -> Unit = {
        exactString(MtgCard::layout, mtgPropertyKeys.LAYOUT) {
            autoValues()
            autoMappings(mtgLayoutMappings)
        }
        property(MtgRarityProperty())
        stringArray(MtgPrint::finishes, propertyKeys.FINISH) { autoMappings(mtgFinishMappings) }
        stringArray(MtgPrint::promoTypes, mtgPropertyKeys.PROMO_TYPE)
        stringArray(MtgPrint::mediums, propertyKeys.MEDIUM)
        stringArray(MtgCardFace::types, mtgPropertyKeys.TYPE)
        stringArray(MtgCardFace::superTypes, mtgPropertyKeys.SUPER_TYPE)
        stringArray(MtgCardFace::subTypes, mtgPropertyKeys.SUB_TYPE)
        stringArray(MtgPrint::frameEffects, mtgPropertyKeys.FRAME_EFFECT) { autoMappings() }
        stringArray(MtgPrintFace::mechanicTags, propertyKeys.MECHANIC) { autoMappings() }
        stringArray(MtgPrintFace::propertyTags, propertyKeys.PROPERTY) { autoMappings(mtgPropertyMappings) }
    }

    filter("is", "has", "tag", "tags") { applyTagProperties() }
    filter("not") {
        inverted(true)
        applyTagProperties()
    }

    filter("legal", "legalformats", "legalin", "format") {
        stringArrayAndCardinality(MtgPrint::formatsLegal, mtgPropertyKeys.LEGAL_FORMATS_COUNT, FormatDescriptor(FormatDescriptor.Type.LEGAL), "legal_format") {
            values(formatProvider)
        }
    }

    filter("restricted", "restrictedformats", "restrictedin") {
        stringArrayAndCardinality(MtgPrint::formatsRestricted, mtgPropertyKeys.RESTRICTED_FORMATS_COUNT, FormatDescriptor(FormatDescriptor.Type.RESTRICTED), "restricted_format") {
            values(formatProvider)
        }
    }

    filter("banned", "bannedformats", "bannedin") {
        stringArrayAndCardinality(MtgPrint::formatsBanned, mtgPropertyKeys.BANNED_FORMATS_COUNT, FormatDescriptor(FormatDescriptor.Type.BANNED), "banned_format") {
            values(formatProvider)
        }
    }

    filter("medium", "mediums", "game", "games") {
        stringArrayAndCardinality(MtgPrint::mediums, propertyKeys.MEDIUM_COUNT, propertyKeys.MEDIUM) { autoMappings(mtgMediumMappings) }
    }

    filter("new") {
        stringArray(MtgPrint::reprintNew, ReprintNewDescriptor(), "reprint_new")
    }

    filter("in") {
        stringArray(MtgCard::reprintIn, ReprintDescriptor(ReprintDescriptor.Mode.REPRINT_IN), "reprint_in")
    }

    filter("promo", "promotypes", "promotype") {
        stringArrayAndCardinality(MtgPrint::promoTypes, mtgPropertyKeys.PROMO_TYPE_COUNT, mtgPropertyKeys.PROMO_TYPE)
    }

    filter("set", "s", "e", "edition", "expansion") {
        uuid(MtgSet::id, propertyKeys.SET_ID)
        string(MtgSet::code, propertyKeys.SET_CODE) {
            autoValues()
            mappings(mtgSetCodeMappings)
        }
    }

    filter("setcode") {
        string(MtgSet::code, propertyKeys.SET_CODE) {
            autoValues()
            mappings(mtgSetCodeMappings)
        }
    }

    filter("setid") { uuid(MtgSet::id, propertyKeys.SET_ID) }
    filter("setname") { string(MtgSet::name, propertyKeys.SET_NAME) { autoValues(false) } }
    filter("settype") { exactString(MtgSet::type, mtgPropertyKeys.SET_TYPE) { autoValues() } }

    filter("layout") {
        exactString(MtgCard::layout, mtgPropertyKeys.LAYOUT) {
            autoValues()
            autoMappings(mtgLayoutMappings)
        }
    }

    filter("typeline", "type", "types", "t") {
        stringArray(MtgCardFace::types, mtgPropertyKeys.TYPE)
        stringArray(MtgCardFace::superTypes, mtgPropertyKeys.SUPER_TYPE)
        stringArray(MtgCardFace::subTypes, mtgPropertyKeys.SUB_TYPE)
        simpleString(MtgCardFaceTranslation::typeLine, MtgCardFaceTranslation::simpleTypeLine, mtgPropertyKeys.TYPE_LINE)
    }

    filter("basetype", "basetypes") {
        stringArrayAndCardinality(MtgCardFace::types, mtgPropertyKeys.TYPE_COUNT, mtgPropertyKeys.TYPE)
    }

    filter("supertype", "supertypes") {
        stringArrayAndCardinality(MtgCardFace::superTypes, mtgPropertyKeys.SUPER_TYPE_COUNT, mtgPropertyKeys.SUPER_TYPE)
    }

    filter("subtype", "subtypes") {
        stringArrayAndCardinality(MtgCardFace::subTypes, mtgPropertyKeys.SUB_TYPE_COUNT, mtgPropertyKeys.SUB_TYPE)
    }

    filter("oracle", "oracletext", "o") {
        simpleString(MtgCardFaceTranslation::oracleText, MtgCardFaceTranslation::simpleOracleText, propertyKeys.TEXT)
    }

    filter("fulloracle", "fulloracletext", "fo") {
        simpleString(MtgCardFaceTranslation::fullOracleText, MtgCardFaceTranslation::simpleFullOracleText, propertyKeys.TEXT_WITH_REMINDERS)
    }

    filter("flavorname", "fn") {
        simpleString(MtgPrintFaceTranslation::flavorName, simpleColumn = MtgPrintFaceTranslation::simpleFlavorName, mtgPropertyKeys.FLAVOR_NAME)
    }

    filter("flavor", "flavortext", "ft") {
        simpleString(MtgPrintFaceTranslation::flavorText, MtgPrintFaceTranslation::simpleFlavorText, propertyKeys.FLAVOR_TEXT)
    }

    filter("frame") {
        stringArray(MtgPrint::frameEffects, mtgPropertyKeys.FRAME_EFFECT) { autoMappings() }
        exactString(MtgPrint::frame, mtgPropertyKeys.FRAME) { autoValues() }
    }

    filter("frameffect", "frameeffects") {
        stringArrayAndCardinality(MtgPrint::frameEffects, mtgPropertyKeys.FRAME_EFFECT_COUNT, mtgPropertyKeys.FRAME_EFFECT) {
            autoMappings()
        }
    }

    filter("stamp") { exactString(MtgPrint::stamp, mtgPropertyKeys.STAMP) { autoValues() } }
    filter("border") { exactString(MtgPrint::border, mtgPropertyKeys.BORDER) { autoValues() } }

    filter("artist", "artists", "illustrator", "illustrators") {
        string(MtgPrint::artist, propertyKeys.ARTIST)
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(MtgPrint::collectorNumberValue, MtgPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("rarity", "r") { property(MtgRarityProperty()) }

    filter("lang", "language", "printlang", "printlanguage") {
        property(MtgPrintLanguageProperty(MtgPrint::languages, MtgPrintFaceTranslation::language))
    }

    filter("langs", "languages", "printlangs", "printlanguages") {
        cardinality(MtgPrint::languages, propertyKeys.LANGUAGE_COUNT)
        property(MtgPrintLanguagesProperty(MtgPrint::languages))
    }

    filter("eur") { numeric(MtgPrintPrice::priceEur, propertyKeys.PRICE_EUR) }
    filter("usd") { numeric(MtgPrintPrice::priceUsd, propertyKeys.PRICE_USD) }
    filter("tix") { numeric(MtgPrintPrice::priceTix, propertyKeys.PRICE_TIX) }

    filter("edhrec", "edhrecrank") {
        numeric(MtgCard::edhrecRank, mtgPropertyKeys.EDHREC_RANK)
    }

    filter("block", "era") {
        uuid(MtgBlock::id, mtgPropertyKeys.BLOCK_ID)
        string(MtgBlock::name, mtgPropertyKeys.BLOCK_NAME) { autoValues(false) }
    }

    filter("blockid", "eraid") { uuid(MtgBlock::id, mtgPropertyKeys.BLOCK_ID) }
    filter("blockname", "eraname") { string(MtgBlock::name, mtgPropertyKeys.BLOCK_NAME) { autoValues() } }

    filter("print", "printid") { uuid(MtgPrint::id, propertyKeys.PRINT_ID) }
    filter("card", "cardid", "oracleid") { uuid(MtgCard::id, propertyKeys.CARD_ID) }
    filter("face", "faceid") { uuid(MtgCardFace::id, propertyKeys.CARD_FACE_ID) }
    filter("printface", "printfaceid") { uuid(MtgPrintFace::id, propertyKeys.PRINT_FACE_ID) }

    filter("scryfall", "scryfallid") {
        exactString(MtgPrintIdentifier::scryfallId, mtgPropertyKeys.SCRYFALL_ID)
    }

    filter("scryfalloracle", "scryfalloracleid", "oscryfall", "oscryfallid") {
        exactString(MtgPrintIdentifier::scryfallOracleId, mtgPropertyKeys.SCRYFALL_ORACLE_ID)
    }

    filter("tcgplayer", "tcgplayerid") {
        exactString(MtgPrintIdentifier::tcgplayerId, mtgPropertyKeys.TCGPLAYER_ID)
    }

    filter("cardkingdom", "cardkingdomid") {
        exactString(MtgPrintIdentifier::cardKingdomId, mtgPropertyKeys.CARDKINGDOM_ID)
    }

    filter("cardmarket", "cardmarketid") {
        exactString(MtgPrintIdentifier::cardmarketId, mtgPropertyKeys.CARDMARKET_ID)
    }

    filter("mtgarena", "mtgarenaid", "arenaid") {
        exactString(MtgPrintIdentifier::mtgArenaId, mtgPropertyKeys.MTGARENA_ID)
    }

    filter("mtgo", "mtgoid") {
        exactString(MtgPrintIdentifier::mtgOnlineId, mtgPropertyKeys.MTGO_ID)
    }

    filter("mtgjson", "mtgjsonid") {
        exactString(MtgPrintIdentifier::mtgjsonId, mtgPropertyKeys.MTGJSON_ID)
    }
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
