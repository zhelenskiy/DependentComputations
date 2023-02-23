package states

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import values.ComputableValue

internal data class NotInitialized internal constructor(
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
