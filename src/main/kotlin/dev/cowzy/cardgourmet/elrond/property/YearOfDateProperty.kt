package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class YearOfDateProperty(column: KProperty1<*, *>, descriptorSubjectKey: String) : NumericExpressionProperty(
    "DATE_PART('Year', ${column.columnName()})",
    arrayOf(column.table()),
    descriptorSubjectKey = descriptorSubjectKey
)