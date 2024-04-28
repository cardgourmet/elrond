package dev.cowzy.cardgourmet.elrond.values

import java.time.LocalDate

class DataYearMappingProvider(private val dateMappingProvider: ValueProvider<Pair<String, LocalDate>>) : ValueProvider<Pair<String, Int>> {

    override suspend fun getValues(): Iterable<Pair<String, Int>> {
        return dateMappingProvider.getValues().map { it.first to it.second.year }
    }

}