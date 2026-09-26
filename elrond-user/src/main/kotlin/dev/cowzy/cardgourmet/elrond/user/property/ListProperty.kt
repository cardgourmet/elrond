package dev.cowzy.cardgourmet.elrond.user.property

import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.table
import java.util.*
import kotlin.reflect.*

data class UuidOrSlug(val uuid: UUID?, val slug: String?)

class ListProperty(
    private val uuidColumn: KProperty1<*, UUID>,
    private val slugColumn: KProperty1<*, String>,
    private val permissionPredicate: suspend (User?, UuidOrSlug) -> Boolean,
) : RestrictedSearchQueryProperty<UuidOrSlug, User>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(uuidColumn.table(), slugColumn.table()),
    descriptor = StringDescriptor(Strings.Query.Collection.Property.BINDER)
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            format = "uuid_or_slug"
            transform { value ->
                try {
                    UuidOrSlug(UUID.fromString(value.value), null)
                } catch (_: IllegalArgumentException) {
                    UuidOrSlug(null, value.value)
                }
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: UuidOrSlug,
        ctx: ColumnContext
    ) {
        value.uuid?.let { builder.where(uuidColumn, it) }
        value.slug?.let { builder.where(slugColumn, it) }
    }

    override suspend fun isPermitted(principal: User?, value: UuidOrSlug) = permissionPredicate(principal, value)

}