package dev.cowzy.cardgourmet.elrond.user

import dev.cowzy.cardgourmet.commons.user.UserCard
import kotlin.reflect.KProperty1

enum class UserCardSortColumn(override val keyword: String, override val properties: Array<KProperty1<*, *>>) :
    dev.cowzy.cardgourmet.elrond.SortColumnDefinition {

    ACQUIRED_AT("acquired_at", UserCard::acquiredAt),
    UPDATED_AT("updated_at", UserCard::updatedAt),
    QUANTITY("quantity", UserCard::amount),
    ACQUIRED_PRICE("acquired_price", UserCard::acquiredPrice);

    constructor(keyword: String, property: KProperty1<*, *>) : this(keyword, arrayOf(property))

}