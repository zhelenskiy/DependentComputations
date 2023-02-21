internal sealed class ComputableValueState<out T> {
    internal abstract val dependents: Set<ComputableValue<*>>
    internal abstract val dependencies: Set<ComputableValue<*>>
    internal abstract fun invalidated(): ComputableValueState<T>
    internal abstract fun withNewDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withNewDependency(dependency: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutAllDependencies(): ComputableValueState<T>

    data class NotInitialized internal constructor(
        override val dependents: Set<ComputableValue<*>>,
        override val dependencies: Set<ComputableValue<*>>,
    ): ComputableValueState<Nothing>() {
        override fun invalidated(): ComputableValueState<Nothing> = this
        override fun withNewDependent(dependent: ComputableValue<*>): NotInitialized =
            copy(dependents = dependents + dependent) // todo make effective
        override fun withNewDependency(dependency: ComputableValue<*>): NotInitialized =
            copy(dependencies = dependencies + dependency) // todo make effective

        override fun withoutAllDependencies(): ComputableValueState<Nothing> = copy(dependencies = setOf())
        override fun withoutDependent(dependent: ComputableValue<*>): ComputableValueState<Nothing> =
            copy(dependents = dependents - dependent) // todo make effective
    }
    data class WithValue<T> internal constructor(
        override val dependents: Set<ComputableValue<*>>,
        override val dependencies: Set<ComputableValue<*>>,
        val cachedValue: Result<T>
    ): ComputableValueState<T>() {
        override fun invalidated(): ComputableValueState<T> = NotInitialized(dependents, dependencies)
        override fun withNewDependent(dependent: ComputableValue<*>): WithValue<T> =
            copy(dependents = dependents + dependent) // todo make effective
        override fun withNewDependency(dependency: ComputableValue<*>): WithValue<T> =
            copy(dependencies = dependencies + dependency) // todo make effective

        override fun withoutAllDependencies(): ComputableValueState<T> = copy(dependencies = setOf())
        override fun withoutDependent(dependent: ComputableValue<*>): WithValue<T> =
            copy(dependents = dependents - dependent) // todo make effective
    }
}