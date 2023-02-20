internal sealed class DependentComputationState<out T> {
    internal abstract val dependents: Set<DependentComputation<*>>
    internal abstract val dependencies: Set<DependentComputation<*>>
    internal abstract fun invalidated(): DependentComputationState<T>
    internal abstract fun withNewDependent(dependent: DependentComputation<*>): DependentComputationState<T>
    internal abstract fun withNewDependency(dependency: DependentComputation<*>): DependentComputationState<T>
    internal abstract fun withoutDependent(dependent: DependentComputation<*>): DependentComputationState<T>
    internal abstract fun withoutAllDependencies(): DependentComputationState<T>

    data class NotInitialized internal constructor(
        override val dependents: Set<DependentComputation<*>>,
        override val dependencies: Set<DependentComputation<*>>,
    ): DependentComputationState<Nothing>() {
        override fun invalidated(): DependentComputationState<Nothing> = this
        override fun withNewDependent(dependent: DependentComputation<*>): NotInitialized =
            copy(dependents = dependents + dependent) // todo make effective
        override fun withNewDependency(dependency: DependentComputation<*>): NotInitialized =
            copy(dependencies = dependencies + dependency) // todo make effective

        override fun withoutAllDependencies(): DependentComputationState<Nothing> = copy(dependencies = setOf())
        override fun withoutDependent(dependent: DependentComputation<*>): DependentComputationState<Nothing> =
            copy(dependents = dependents - dependent) // todo make effective
    }
    data class WithValue<T> internal constructor(
        override val dependents: Set<DependentComputation<*>>,
        override val dependencies: Set<DependentComputation<*>>,
        val cachedValue: Result<T>
    ): DependentComputationState<T>() {
        override fun invalidated(): DependentComputationState<T> = NotInitialized(dependents, dependencies)
        override fun withNewDependent(dependent: DependentComputation<*>): WithValue<T> =
            copy(dependents = dependents + dependent) // todo make effective
        override fun withNewDependency(dependency: DependentComputation<*>): WithValue<T> =
            copy(dependencies = dependencies + dependency) // todo make effective

        override fun withoutAllDependencies(): DependentComputationState<T> = copy(dependencies = setOf())
        override fun withoutDependent(dependent: DependentComputation<*>): WithValue<T> =
            copy(dependents = dependents - dependent) // todo make effective
    }
}