package states

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import values.PrimitiveComputableValue

internal data class WithValue<out T> internal constructor(
    override val dependents: PersistentSet<PrimitiveComputableValue<*>>,
    override val dependencies: PersistentSet<PrimitiveComputableValue<*>>,
    val cachedValue: Result<T>
): ComputableValueState<T>() {
    override fun invalidated(): ComputableValueState<T> = NotInitialized(dependents, dependencies)
    override fun withNewDependent(dependent: PrimitiveComputableValue<*>): WithValue<T> =
        copy(dependents = dependents.add(dependent))
    override fun withNewDependency(dependency: PrimitiveComputableValue<*>): WithValue<T> =
        copy(dependencies = dependencies.add(dependency))

    override fun withoutAllDependencies(): ComputableValueState<T> = copy(dependencies = persistentSetOf())
    override fun withoutDependent(dependent: PrimitiveComputableValue<*>): WithValue<T> =
        copy(dependents = dependents.remove(dependent))
}
