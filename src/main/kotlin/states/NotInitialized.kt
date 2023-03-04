package states

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import values.PrimitiveComputableValue

internal data class NotInitialized internal constructor(
    override val dependents: PersistentSet<PrimitiveComputableValue<*>>,
    override val dependencies: PersistentSet<PrimitiveComputableValue<*>>,
): ComputableValueState<Nothing>() {
    override fun invalidated(): ComputableValueState<Nothing> = this
    override fun withNewDependent(dependent: PrimitiveComputableValue<*>): NotInitialized =
        copy(dependents = dependents.add(dependent))
    override fun withNewDependency(dependency: PrimitiveComputableValue<*>): NotInitialized =
        copy(dependencies = dependencies.add(dependency))

    override fun withoutAllDependencies(): ComputableValueState<Nothing> = copy(dependencies = persistentSetOf())
    override fun withoutDependent(dependent: PrimitiveComputableValue<*>): ComputableValueState<Nothing> =
        copy(dependents = dependents.remove(dependent))
}
