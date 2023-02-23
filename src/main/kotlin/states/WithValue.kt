package states

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import values.ComputableValue

internal data class WithValue<out T> internal constructor(
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
