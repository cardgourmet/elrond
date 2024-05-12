package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcLanguage
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.user.UserCardAcquisition
import dev.cowzy.cardgourmet.commons.user.UserCardBinder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.config.dlc.dlcBasicSearchQueryConfig
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

fun SearchQueryConfigBuilder.configureDlcCollectionFilters() {
    filter("finishes", "finish") {
        // TODO: value provider
        stringArrayAndCardinality(UserCard::finishes, propertyKeys.FINISH_COUNT, propertyKeys.FINISH)
    }

    filter("medium", "mediums") {
        exactString(UserCard::medium, propertyKeys.MEDIUM) { values("paper", type = "medium") }
    }

    filter("lang", "language", "userlang", "userlanguage") {
        exactString(UserCard::language, collectionPropertyKeys.LANGUAGE) {
            enumValues<DlcLanguage>("language", transform = { it.getSerialName() })
        }
    }

    // TODO: is:foil/not:foil
}

private val tableDependencies = mapOf(
    UserCard::class to TableDependency(DlcPrint::class, DlcCardTranslation::class) { builder ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(UserCard::printId, DlcPrint::id)
                .where(UserCard::game, GameType.DISNEY_LORCANA)
        }
    },
    UserCardBinder::class to TableDependency(UserCard::class) { builder ->
        builder.leftJoin(UserCardBinder::class) { it.whereColumn(UserCardBinder::id, UserCard::binderId) }
    },
    UserCardAcquisition::class to TableDependency(UserCard::class) { builder ->
        builder.leftJoin(UserCardAcquisition::class) { it.whereColumn(UserCardAcquisition::id, UserCard::acquisitionId) }
    },
    User::class to TableDependency(UserCard::class) { builder ->
        builder.innerJoin(User::class) { it.whereColumn(User::id, UserCard::userId) }
    },
)

val dlcSearchQueryConfig = dlcBasicSearchQueryConfig.copy(
    languageColumns = arrayOf(UserCard::language, *dlcBasicSearchQueryConfig.languageColumns),
    tableDependencies = dlcBasicSearchQueryConfig.tableDependencies + tableDependencies,
)
