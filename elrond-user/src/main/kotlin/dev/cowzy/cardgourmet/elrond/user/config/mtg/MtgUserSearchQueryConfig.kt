package dev.cowzy.cardgourmet.elrond.user.config.mtg

import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgCardFaceTranslation
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgFinish
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgLanguage
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgMedium
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.config.CustomField
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.user.property.mtg.MtgUserCardFoilProperty
import dev.cowzy.cardgourmet.tcg.config.card.mtg.mtgBasicSearchQueryConfig
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

fun SearchQueryFilterBuilder.configureMtgCollectionFilters() {
//    val nameValueProvider = valueProviderPool.getOrPut("mtg_name") { MtgNameValueProvider(it) }
//    filter("name", "n") {
//        property(MtgUserNameProperty(nameValueProvider))
//    }

    filter("finishes", "finish") {
        stringArrayAndCardinality(UserCard::finishes, propertyKeys.FINISH_COUNT, propertyKeys.FINISH) {
            enumValues<MtgFinish>("finish", findKeywords = { it.keys }, transform = { it.getSerialName() })
        }
    }

    filter("medium", "mediums") {
        exactString(UserCard::medium, propertyKeys.MEDIUM) {
            enumValues<MtgMedium>("medium", findKeywords = { it.keys }, transform = { it.getSerialName() })
        }
    }

    filter("lang", "language", "userlang", "userlanguage") {
        exactString(UserCard::language, collectionPropertyKeys.LANGUAGE) {
            enumValues<MtgLanguage>("language", findKeywords = { it.keys }, transform = { it.getSerialName() })
        }
    }

    filter("is:foil") {
        property(MtgUserCardFoilProperty())
    }

    filter("not:foil") {
        inverted(true)
        property(MtgUserCardFoilProperty())
    }
}

private val tableDependencies = mapOf(
    UserCard::class to TableDependency(MtgPrint::class, MtgCardFaceTranslation::class) { builder ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(UserCard::printId, MtgPrint::id)
                .where(UserCard::game, GameType.MAGIC_THE_GATHERING)
        }
    },
    UserCardBinder::class to TableDependency(UserCard::class) { builder ->
        builder.leftJoin(UserCardBinder::class) { it.whereColumn(UserCardBinder::id, UserCard::binderId) }
    },
    User::class to TableDependency(UserCard::class) { builder ->
        builder.innerJoin(User::class) { it.whereColumn(User::id, UserCard::userId) }
    },
)

@Suppress("UNCHECKED_CAST")
val mtgSearchQueryConfig = mtgBasicSearchQueryConfig.copy(
    customFields = mtgBasicSearchQueryConfig.customFields.toMutableMap().apply {
        val field = this["language"]!! as CustomField<LanguageCode>
        this["language"] = CustomField(UserCard::language, *field.properties.toTypedArray())
    },
    tableDependencies = mtgBasicSearchQueryConfig.tableDependencies + tableDependencies,
)
