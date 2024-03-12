package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.database.user.UserCard
import dev.cowzy.cardgourmet.elrond.SortColumnDefinition
import kotlin.reflect.KProperty1

enum class UserCardSortColumn(override val keyword: String, override val properties: Array<KProperty1<*, *>>) : SortColumnDefinition {

    ACQUIRED_AT("acquired_at", UserCard::acquiredAt),
    UPDATED_AT("updated_at", UserCard::updatedAt),
    QUANTITY("quantity", UserCard::amount),
    ACQUIRED_PRICE("acquired_price", UserCard::acquiredPrice);

    constructor(keyword: String, property: KProperty1<*, *>) : this(keyword, arrayOf(property))

}