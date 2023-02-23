package states

import kotlinx.collections.immutable.PersistentSet
import values.ComputableValue

internal sealed class ComputableValueState<out T> {
    internal abstract val dependents: PersistentSet<ComputableValue<*>>
    internal abstract val dependencies: PersistentSet<ComputableValue<*>>
    internal abstract fun invalidated(): ComputableValueState<T>
    internal abstract fun withNewDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withNewDependency(dependency: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutDependent(dependent: ComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutAllDependencies(): ComputableValueState<T>

}

@Suppress("UNCHECKED_CAST")
internal val ComputableValueState<*>.casted get() = this as ComputableValueState<Nothing>
