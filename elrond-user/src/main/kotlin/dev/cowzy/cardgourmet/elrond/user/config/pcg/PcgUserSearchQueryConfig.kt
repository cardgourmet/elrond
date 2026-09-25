package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgLanguage
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.config.CustomField
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.tcg.config.card.pcg.pcgBasicCardSearchQueryConfig
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

fun SearchQueryFilterBuilder.configurePcgCollectionFilters() {
    filter("medium", "mediums") {
        exactString(UserCard::medium, propertyKeys.MEDIUM) { values("paper", type = "medium") }
    }

    filter("lang", "language", "userlang", "userlanguage") {
        exactString(UserCard::language, collectionPropertyKeys.LANGUAGE) {
            enumValues<PcgLanguage>("language", findKeywords = { it.keys }, transform = { it.getSerialName() })
        }
    }

    // TODO: finishes
    // TODO: is:foil/not:foil
}

private val tableDependencies = mapOf(
    UserCard::class to TableDependency(PcgPrint::class, PcgCardTranslation::class) { builder, ctx ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(ctx.resolve(UserCard::printId), ctx.resolve(PcgPrint::id))
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
val pcgSearchQueryConfig = pcgBasicCardSearchQueryConfig.copy(
    customFields = pcgBasicCardSearchQueryConfig.customFields.toMutableMap().apply {
        val field = this["language"]!! as CustomField<LanguageCode>
        this["language"] = CustomField(UserCard::language, *field.properties.toTypedArray())
    },
    tableDependencies = pcgBasicCardSearchQueryConfig.tableDependencies + tableDependencies,
)
