package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcLanguage
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.config.CustomField
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.tcg.config.card.dlc.dlcBasicCardSearchQueryConfig
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

fun SearchQueryFilterBuilder.configureDlcCollectionFilters() {
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
    UserCard::class to TableDependency(DlcPrint::class, DlcCardTranslation::class) { builder, ctx ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(ctx.resolve(UserCard::printId), ctx.resolve(DlcPrint::id))
                .where(ctx.resolve(UserCard::game), GameType.DISNEY_LORCANA)
        }
    },
    UserCardBinder::class to TableDependency(UserCard::class) { builder, ctx ->
        builder.leftJoin(UserCardBinder::class) { it.whereColumn(ctx.resolve(UserCardBinder::id), ctx.resolve(UserCard::binderId)) }
    },
    User::class to TableDependency(UserCard::class) { builder, ctx ->
        builder.innerJoin(User::class) { it.whereColumn(ctx.resolve(User::id), ctx.resolve(UserCard::userId)) }
    },
)

@Suppress("UNCHECKED_CAST")
val dlcSearchQueryConfig = dlcBasicCardSearchQueryConfig.copy(
    customFields = dlcBasicCardSearchQueryConfig.customFields.toMutableMap().apply {
        val field = this["language"]!! as CustomField<LanguageCode>
        this["language"] = CustomField(UserCard::language, *field.properties.toTypedArray())
    },
    tableDependencies = dlcBasicCardSearchQueryConfig.tableDependencies + tableDependencies,
)
