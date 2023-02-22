import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

internal sealed class ComputableValueState<out T> {
    internal abstract val dependents: PersistentSet<ComputableValue<*>>
    internal abstract val dependencies: PersistentSet<ComputableValue<*>>
    internal abstract fun invalidated(): ComputableValueState<T>
    internal abstract fun withNewDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withNewDependency(dependency: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutAllDependencies(): ComputableValueState<T>

    data class NotInitialized internal constructor(
        override val dependents: PersistentSet<ComputableValue<*>>,
        override val dependencies: PersistentSet<ComputableValue<*>>,
    ): ComputableValueState<Nothing>() {
        override fun invalidated(): ComputableValueState<Nothing> = this
        override fun withNewDependent(dependent: ComputableValue<*>): NotInitialized =
            copy(dependents = dependents.add(dependent))
        override fun withNewDependency(dependency: ComputableValue<*>): NotInitialized =
            copy(dependencies = dependencies.add(dependency))

        override fun withoutAllDependencies(): ComputableValueState<Nothing> = copy(dependencies = persistentSetOf())
        override fun withoutDependent(dependent: ComputableValue<*>): ComputableValueState<Nothing> =
            copy(dependents = dependents.remove(dependent))
    }
    data class WithValue<out T> internal constructor(
        override val dependents: PersistentSet<ComputableValue<*>>,
        override val dependencies: PersistentSet<ComputableValue<*>>,
        val cachedValue: Result<T>
    ): ComputableValueState<T>() {
        override fun invalidated(): ComputableValueState<T> = NotInitialized(dependents, dependencies)
        override fun withNewDependent(dependent: ComputableValue<*>): WithValue<T> =
            copy(dependents = dependents.add(dependent))
        override fun withNewDependency(dependency: ComputableValue<*>): WithValue<T> =
            copy(dependencies = dependencies.add(dependency))

        override fun withoutAllDependencies(): ComputableValueState<T> = copy(dependencies = persistentSetOf())
        override fun withoutDependent(dependent: ComputableValue<*>): WithValue<T> =
            copy(dependents = dependents.remove(dependent))
    }
}

@Suppress("UNCHECKED_CAST")
internal val ComputableValueState<*>.casted get() = this as ComputableValueState<Nothing>
