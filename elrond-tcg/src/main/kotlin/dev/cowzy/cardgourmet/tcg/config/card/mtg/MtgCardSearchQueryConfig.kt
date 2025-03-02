package dev.cowzy.cardgourmet.tcg.config.card.mtg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.image.CardImageColor
import dev.cowzy.cardgourmet.commons.*
import dev.cowzy.cardgourmet.chef.commons.model.card.CardPrice
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
import dev.cowzy.cardgourmet.commons.database.deck.MtgFormat
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.chef.commons.model.set.mtg.MtgBlock
import dev.cowzy.cardgourmet.chef.commons.model.set.mtg.MtgSet
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.descriptor.AvailableInDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.values.ValueProviderBuilder
import dev.cowzy.cardgourmet.elrond.values.autoArrayValues
import dev.cowzy.cardgourmet.elrond.values.autoValues
import dev.cowzy.cardgourmet.tcg.descriptor.mtg.FormatDescriptor
import dev.cowzy.cardgourmet.tcg.descriptor.mtg.ManaColorsDescriptor
import dev.cowzy.cardgourmet.tcg.descriptor.mtg.ReprintDescriptor
import dev.cowzy.cardgourmet.tcg.descriptor.mtg.ReprintNewDescriptor
import dev.cowzy.cardgourmet.tcg.property.mtg.*
import dev.cowzy.kuery.query.QueryBuilder
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.parse
import java.sql.Connection
import java.time.format.DateTimeFormatter

val manaCardinalityMappings = mapOf(
    "monocolor" to (1 to SearchQueryOperator.EQUALS),
    "bicolor" to (2 to SearchQueryOperator.EQUALS),
    "dualcolor" to (2 to SearchQueryOperator.EQUALS),
    "tricolor" to (3 to SearchQueryOperator.EQUALS),
    "quadcolor" to (4 to SearchQueryOperator.EQUALS),
    "omnicolor" to (5 to SearchQueryOperator.EQUALS),
    "multicolor" to (2 to SearchQueryOperator.GREATER_THAN_OR_EQUALS),
    "none" to (0 to SearchQueryOperator.EQUALS),
    "any" to (1 to SearchQueryOperator.GREATER_THAN_OR_EQUALS)
)

val manaCardinalityMappingsWithColorless = manaCardinalityMappings + mapOf(
    "colorless" to (0 to SearchQueryOperator.EQUALS),
    "c" to (0 to SearchQueryOperator.EQUALS)
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
val mtgSetCodeMappings = mapOf("PLIST" to "PLST", "ULIST" to "ULST")

private val propertyKeys = Strings.Query.Property
private val mtgPropertyKeys = Strings.Query.Mtg.Property

private val getSetReleaseDates = { connection: Connection ->
    MtgSet::class.selectBuilder()
        .distinctOn(MtgSet::code)
        .select(MtgSet::code)
        .select(MtgSet::releaseDate)
        .get(connection) { row, index ->
            val setCode = MtgSet::code.parse(row, index)
            val date = MtgSet::releaseDate.parse(row, index)
            setCode to date
        }.toMap()
}

private val getNames = { connection: Connection ->
    QueryBuilder.selectBuilder("mtg.search_names")
        .distinct()
        .select("mtg.search_names.name")
        .select("mtg.search_names.language")
        .orderBy("mtg.search_names.name")
        .get(connection) { row, index ->
            val name = row.getString(index.getAndIncrement())
            val language = row.getString(index.getAndIncrement())
            language to name
        }.groupBy { it.first }.mapValues { it.value.associate { (_, name) -> name to name } }
}

private val getNameWordBank = { connection: Connection -> getNames(connection).toWordBank() }

private fun ValueProviderBuilder<List<ManaValue>>.manaValues() {
    enumValues<MtgManaType>(
        "color",
        findKeywords = { listOf(it.symbol.lowercase()) },
        transform = { listOf(ConcreteManaValue(it)) }
    )

    enumValues<MtgManaColorNicknames>(
        "color_nickname",
        transform = { it.colors.map(::ConcreteManaValue) }
    )

    values(mapOf("all" to MtgManaType.colors.map(::ConcreteManaValue)), "string")
}

fun SearchQueryFilterBuilder.configureBasicMtgCardFilters() {
    filter("name", "n") {
        property(MtgNameProperty()) {
            valuesWithLanguage(getNames, { StringValue(it, true) }, "name")
            valuesWithLanguage(getNameWordBank, { StringValue(it, false) }, "name_part")
        }
    }

    filter("manavalue", "cmc", "mv") { numeric(MtgCardFace::manaValue, mtgPropertyKeys.MANA_VALUE) }
    filter("manadisplay", "display", "mana", "m") { property(MtgManaDisplayProperty()) }
    filter("devotion") { property(MtgDevotionProperty()) }

    filter("color", "colors", "c") {
        ignoreReference("c")
        cardinality(MtgCardFace::colors, mtgPropertyKeys.COLOR_COUNT, manaCardinalityMappingsWithColorless)
        property(
            MtgManaArrayColumnProperty(
                MtgCardFace::colors,
                descriptor = ManaColorsDescriptor(
                    mtgPropertyKeys.MANA_COLORS,
                    mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS
                )
            )
        ) {
            transform { items -> items.joinToString("") { ManaDisplay(it).toString() } }
            manaValues()
        }
    }

    filter("produces", "producedcolors") {
        cardinality(
            MtgCardFace::producesMana,
            mtgPropertyKeys.PRODUCED_MANA_COUNT,
            manaCardinalityMappings,
            distinctValues = true
        )
        property(
            MtgManaArrayColumnProperty(
                MtgCardFace::producesMana,
                descriptor = NumericDescriptor(
                    mtgPropertyKeys.PRODUCED_MANA,
                    mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS
                )
            )
        ) {
            transform { items -> items.joinToString("") { ManaDisplay(it).toString() } }
            manaValues()
        }
    }

//    filter("producedamount") {
//        // TODO: Custom property key
//        cardinality(MtgCardFace::producesMana, mtgPropertyKeys.PRODUCED_MANA_COUNT, manaCardinalityMappings)
//    }

    filter("coloridentity", "id", "identity", "ci", "commander") {
        ignoreReference("commander")
        cardinality(MtgCard::colorIdentity, mtgPropertyKeys.COLOR_IDENTITY_COUNT, manaCardinalityMappingsWithColorless)
        property(
            MtgManaArrayColumnProperty(
                MtgCard::colorIdentity,
                true,
                NumericDescriptor(
                    mtgPropertyKeys.COLOR_IDENTITY,
                    mapContainsTo = SearchQueryOperator.LESS_THAN_OR_EQUALS
                )
            )
        ) {
            transform { items -> items.joinToString("") { ManaDisplay(it).toString() } }
            manaValues()
        }
    }

    filter("indicator", "indicatorcolors") {
        cardinality(
            MtgCardFace::colorIndicator,
            mtgPropertyKeys.COLOR_INDICATOR_COUNT,
            manaCardinalityMappingsWithColorless
        )
        property(
            MtgManaArrayColumnProperty(
                MtgCardFace::colorIndicator,
                descriptor = NumericDescriptor(
                    mtgPropertyKeys.COLOR_INDICATOR,
                    mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS
                )
            )
        ) {
            transform { items -> items.joinToString("") { ManaDisplay(it).toString() } }
            manaValues()
        }
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

    filter("combinedpt", "pt", "powtou", "heft") {
        numeric(
            MtgCardFace::powerValue,
            MtgCardFace::toughnessValue,
            propertyKey = mtgPropertyKeys.COMBINED_POWER_TOUGHNESS
        )
    }

    filter("faces", "facecount") { numeric(MtgCard::faceCount, propertyKeys.FACE_COUNT) }
    filter("face", "facenumber") { numeric(MtgCardFace::index, propertyKeys.FACE_NUMBER, 1.0) }

    filter("year", "releaseyear") {
        year(MtgPrint::releaseDate, propertyKeys.RELEASE_YEAR) {
            values(getSetReleaseDates, { it.year }, "set_code", merge = false)
        }
    }

    filter("date", "releasedate") {
        date(MtgPrint::releaseDate, propertyKeys.RELEASE_DATE) {
            values(getSetReleaseDates, { it.format(DateTimeFormatter.ISO_DATE) }, "set_code", merge = false)
        }
    }

    filter("prints", "printcount") { property(MtgPrintCountProperty()) }
    filter("paperprints", "paperprintcount") { property(MtgPaperPrintCountProperty()) }
    filter("sets", "setcount") { property(MtgSetCountProperty()) }
    filter("papersets", "papersetcount") { property(MtgPaperSetCountProperty()) }

    filter("finish", "finishes") {
        stringArrayAndCardinality(MtgPrint::finishes, propertyKeys.FINISH_COUNT, propertyKeys.FINISH) {
            strict(true)
            autoArrayValues(MtgPrint::finishes, "finish", true)
            values(mtgFinishMappings, "finish")
        }
    }

    filter("watermark", "watermarks", "wm") {
        stringArrayAndCardinality(MtgPrint::watermarks, mtgPropertyKeys.WATERMARK_COUNT, mtgPropertyKeys.WATERMARK)
    }

    filter("keyword", "keywords", "key") {
        stringArrayAndCardinality(MtgCard::keywords, propertyKeys.KEYWORD_COUNT, propertyKeys.KEYWORD)
    }

    filter("mechanic", "mechanics", "function", "otag", "oracletag") {
        stringArrayAndCardinality(MtgPrintFace::mechanicTags, propertyKeys.MECHANIC_COUNT, propertyKeys.MECHANIC) {
            strict(true)
            autoArrayValues(MtgPrintFace::mechanicTags, "mechanic", true)
        }
    }

    filter("property", "properties") {
        stringArrayAndCardinality(MtgPrintFace::propertyTags, propertyKeys.PROPERTY_COUNT, propertyKeys.PROPERTY) {
            strict(true)
            autoArrayValues(MtgPrintFace::propertyTags, "property", true)
            values(mtgPropertyMappings, "property")
        }
    }

    filter("art") {
        stringArrayAndCardinality(MtgPrintFace::artTags, propertyKeys.ART_TAGS_COUNT, propertyKeys.ART_TAGS)
    }

    val applyTagProperties: QueryFilterBuilder.() -> Unit = {
        stringArray(MtgPrintFace::propertyTags, propertyKeys.PROPERTY) {
            strict(true)
            autoArrayValues(MtgPrintFace::propertyTags, "property", true)
            values(mtgPropertyMappings, "property")
        }
        exactString(MtgCard::layout, mtgPropertyKeys.LAYOUT) {
            strict(true)
            autoValues(MtgCard::layout, "layout", autoAlias = true)
            values(mtgLayoutMappings, "layout")
        }
        enum<MtgRarity>(MtgPrint::rarity, propertyKeys.RARITY) { it.keywords.toList() }
        stringArray(MtgPrint::finishes, propertyKeys.FINISH) {
            strict(true)
            autoArrayValues(MtgPrint::finishes, "finish", true)
            values(mtgFinishMappings, "finish")
        }
        stringArray(MtgPrint::promoTypes, mtgPropertyKeys.PROMO_TYPE)
        stringArray(MtgPrint::mediums, propertyKeys.MEDIUM)
        stringArray(MtgCardFace::types, mtgPropertyKeys.TYPE)
        stringArray(MtgCardFace::superTypes, mtgPropertyKeys.SUPER_TYPE)
        stringArray(MtgCardFace::subTypes, mtgPropertyKeys.SUB_TYPE)
        stringArray(MtgPrint::frameEffects, mtgPropertyKeys.FRAME_EFFECT)
        stringArray(MtgPrintFace::mechanicTags, propertyKeys.MECHANIC) {
            strict(true)
            autoArrayValues(MtgPrintFace::mechanicTags, "mechanic", true)
        }
    }

    filter("is", "has", "tag", "tags") { applyTagProperties() }
    filter("not") {
        inverted(true)
        applyTagProperties()
    }

    filter("legal", "legalformats", "legalin", "format") {
        stringArrayAndCardinality(
            MtgPrint::formatsLegal, mtgPropertyKeys.LEGAL_FORMATS_COUNT, FormatDescriptor(
                FormatDescriptor.Type.LEGAL
            ), "legal_format"
        ) {
            strict(true)
            enumValues<MtgFormat>("format", transform = { it.getSerialName() })
        }
    }

    filter("restricted", "restrictedformats", "restrictedin") {
        stringArrayAndCardinality(
            MtgPrint::formatsRestricted, mtgPropertyKeys.RESTRICTED_FORMATS_COUNT, FormatDescriptor(
                FormatDescriptor.Type.RESTRICTED
            ), "restricted_format"
        ) {
            strict(true)
            enumValues<MtgFormat>("format", transform = { it.getSerialName() })
        }
    }

    filter("banned", "bannedformats", "bannedin") {
        stringArrayAndCardinality(
            MtgPrint::formatsBanned, mtgPropertyKeys.BANNED_FORMATS_COUNT, FormatDescriptor(
                FormatDescriptor.Type.BANNED
            ), "banned_format"
        ) {
            strict(true)
            enumValues<MtgFormat>("format", transform = { it.getSerialName() })
        }
    }

    filter("medium", "mediums", "game", "games") {
        stringArrayAndCardinality(MtgPrint::mediums, propertyKeys.MEDIUM_COUNT, propertyKeys.MEDIUM) {
            strict(true)
            enumValues<MtgMedium>("medium", findKeywords = { it.keys }, transform = { it.getSerialName() })
            values(mtgMediumMappings, "medium")
        }
    }

    filter("new") {
        stringArray(MtgPrint::reprintNew, ReprintNewDescriptor(), "reprint_new")
    }

    filter("in") {
        stringArray(MtgCard::reprintIn, ReprintDescriptor(ReprintDescriptor.Mode.REPRINT_IN), "reprint_in")
    }

    filter("promo", "promotype", "promotypes") {
        stringArrayAndCardinality(MtgPrint::promoTypes, mtgPropertyKeys.PROMO_TYPE_COUNT, mtgPropertyKeys.PROMO_TYPE)
    }

    filter("set", "s", "e", "edition", "expansion") {
        uuid(MtgSet::id, propertyKeys.SET_ID)
        string(MtgSet::code, propertyKeys.SET_CODE) {
            autoValues(MtgSet::code, "set_code")
            values(mtgSetCodeMappings, "set_code")
        }
    }

    filter("setcode") {
        string(MtgSet::code, propertyKeys.SET_CODE) {
            autoValues(MtgSet::code, "set_code")
            values(mtgSetCodeMappings, "set_code")
        }
    }

    filter("setid") { uuid(MtgSet::id, propertyKeys.SET_ID) }
    filter("setname") { string(MtgSet::name, propertyKeys.SET_NAME) { autoValues(MtgSet::name, "set_name") } }
    filter("settype") { exactString(MtgSet::type, mtgPropertyKeys.SET_TYPE) { autoValues(MtgSet::type, "set_type") } }

    filter("layout") {
        exactString(MtgCard::layout, mtgPropertyKeys.LAYOUT) {
            autoValues(MtgCard::layout, "layout", autoAlias = true)
            values(mtgLayoutMappings, "layout")
        }
    }

    filter("type", "typeline", "types", "t") {
        cardinality(
            MtgCardFace::types,
            MtgCardFace::superTypes,
            MtgCardFace::subTypes,
            propertyKey = mtgPropertyKeys.TYPE_COUNT
        )
        stringArray(MtgCardFace::types, mtgPropertyKeys.TYPE)
        stringArray(MtgCardFace::superTypes, mtgPropertyKeys.SUPER_TYPE)
        stringArray(MtgCardFace::subTypes, mtgPropertyKeys.SUB_TYPE)
        simpleString(
            MtgCardFaceTranslation::typeLine,
            MtgCardFaceTranslation::simpleTypeLine,
            mtgPropertyKeys.TYPE_LINE
        )
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

    filter("text", "oracle", "oracletext", "o") {
        simpleString(MtgCardFaceTranslation::oracleText, MtgCardFaceTranslation::simpleOracleText, propertyKeys.TEXT)
    }

    filter("fulloracle", "fulloracletext", "fo") {
        simpleString(
            MtgCardFaceTranslation::fullOracleText,
            MtgCardFaceTranslation::simpleFullOracleText,
            propertyKeys.TEXT_WITH_REMINDERS
        )
    }

    filter("flavorname", "fn") {
        simpleString(
            MtgPrintFaceTranslation::flavorName,
            simpleColumn = MtgPrintFaceTranslation::simpleFlavorName,
            mtgPropertyKeys.FLAVOR_NAME
        )
    }

    filter("flavor", "flavortext", "ft") {
        simpleString(
            MtgPrintFaceTranslation::flavorText,
            MtgPrintFaceTranslation::simpleFlavorText,
            propertyKeys.FLAVOR_TEXT
        )
    }

    filter("frame") {
        stringArray(MtgPrint::frameEffects, mtgPropertyKeys.FRAME_EFFECT)
        exactString(MtgPrint::frame, mtgPropertyKeys.FRAME) {
            strict(true)
            autoValues(MtgPrint::frame)
        }
    }

    filter("frameeffect", "frameeffects", "frameffect") {
        stringArrayAndCardinality(
            MtgPrint::frameEffects,
            mtgPropertyKeys.FRAME_EFFECT_COUNT,
            mtgPropertyKeys.FRAME_EFFECT
        )
    }

    filter("stamp") {
        exactString(MtgPrint::stamp, mtgPropertyKeys.STAMP) {
            strict(true)
            autoValues(MtgPrint::stamp)
        }
    }
    filter("border") {
        exactString(MtgPrint::border, mtgPropertyKeys.BORDER) {
            strict(true)
            autoValues(MtgPrint::border)
        }
    }

    filter("artist", "artists", "illustrator", "illustrators") {
        string(MtgPrint::artist, propertyKeys.ARTIST)
    }

    filter("collectornumber", "cn", "number") {
        numericAndString(MtgPrint::collectorNumberValue, MtgPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("rarity", "r") { property(MtgRarityProperty(valueProviderPool)) }

    filter("lang", "language", "printlang", "printlanguage") {
        enum<MtgLanguage>(
            MtgPrintFaceTranslation::language,
            AvailableInDescriptor(propertyKeys.PRINT),
            "print_language",
            aliasResolver = { it.keys },
            display = { value, i18n, locale ->
                i18n.translate(locale, "${Strings.Query.Mtg.Language.KEY}.${value.getSerialName()}")
            }
        )
    }

    filter("cardlang", "cardlanguage") {
        enum<MtgLanguage>(
            MtgCardFaceTranslation::language,
            AvailableInDescriptor(propertyKeys.CARD),
            "card_language",
            display = { value, i18n, locale ->
                i18n.translate(locale, "${Strings.Query.Dlc.Language.KEY}.${value.getSerialName()}")
            })
    }

    filter("eur") { numeric(MtgPrintPrice::priceEur, propertyKeys.PRICE_EUR) }
    filter("usd") { numeric(MtgPrintPrice::priceUsd, propertyKeys.PRICE_USD) }
    filter("tix") { numeric(MtgPrintPrice::priceTix, propertyKeys.PRICE_TIX) }

    filter("edhrec", "edhrecrank") {
        numeric(MtgCard::edhrecRank, mtgPropertyKeys.EDHREC_RANK)
    }

    filter("block", "era") {
        uuid(MtgBlock::id, mtgPropertyKeys.BLOCK_ID)
        string(MtgBlock::name, mtgPropertyKeys.BLOCK_NAME) { autoValues(MtgBlock::name, "block_name") }
    }

    filter("blockid", "eraid") { uuid(MtgBlock::id, mtgPropertyKeys.BLOCK_ID) }
    filter("blockname", "eraname") {
        string(MtgBlock::name, mtgPropertyKeys.BLOCK_NAME) {
            autoValues(
                MtgBlock::name,
                "block_name"
            )
        }
    }

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

    filter("artworkcolor", "artcolor") {
        stringArray(CardImageColor::nearestColors, propertyKeys.ARTWORK_COLOR)
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
        builder.leftJoin(CardImage::class) {
            it.whereColumn(
                CardImage::printTranslationId,
                MtgPrintFaceTranslation::id
            )
        }
    },
    CardImageColor::class to TableDependency(MtgPrintFaceTranslation::class) { builder ->
        builder.leftJoin(CardImageColor::class) {
            it
                .where(CardImageColor::game, GameType.DISNEY_LORCANA)
                .whereColumn(CardImageColor::printTranslationId, MtgPrintFaceTranslation::id)
        }
    }
)

val mtgBasicSearchQueryConfig = SearchQuerySqlConfig(
    baseTable = MtgPrint::class,
    tableDependencies = tableDependencies,
    customFields = mapOf(
        "printId" to CustomField(MtgPrint::id),
        "faceIndex" to CustomField(MtgCardFace::index),
        "language" to CustomField(MtgPrintFaceTranslation::language, MtgCardFaceTranslation::language)
    )
)
