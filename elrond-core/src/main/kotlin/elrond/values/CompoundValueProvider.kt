package elrond.values

class CompoundValueProvider<T>(private vararg val providers: ValueProvider<T>) : ValueProvider<T> {
    override suspend fun getValues(): Set<T> {
        return this.providers.map { it.getValues() }.flatten().toSet()
    }
}