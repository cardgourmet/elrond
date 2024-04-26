package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.values.DynamicStringValueProvider
import dev.cowzy.kuery.query.QueryBuilder

class MtgNameValueProvider(val pool: SqlDatabasePool) : DynamicStringValueProvider {

    override suspend fun getValues(limit: Int, filter: String?): List<String> {
        return pool.use { connection ->
            QueryBuilder.selectBuilder("mtg.search_names")
                .distinct()
                .select("mtg.search_names.name")
                .apply {
                    filter?.let {
                        this.where("mtg.search_names.simple_name", "%${it.toSimpleString()}%")
                    }
                }
                .orderBy("mtg.search_names.simple_name")
                .limit(limit)
                .get(connection) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }

}