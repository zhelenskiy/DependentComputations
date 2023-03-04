package states

import kotlinx.collections.immutable.PersistentSet
import values.PrimitiveComputableValue

internal sealed class ComputableValueState<out T> {
    internal abstract val dependents: PersistentSet<PrimitiveComputableValue<*>>
    internal abstract val dependencies: PersistentSet<PrimitiveComputableValue<*>>
    internal abstract fun invalidated(): ComputableValueState<T>
    internal abstract fun withNewDependent(dependent: PrimitiveComputableValue<*>): ComputableValueState<T>
    internal abstract fun withNewDependency(dependency: PrimitiveComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutDependent(dependent: PrimitiveComputableValue<*>): ComputableValueState<T>
    internal abstract fun withoutAllDependencies(): ComputableValueState<T>

}

@Suppress("UNCHECKED_CAST")
internal val ComputableValueState<*>.casted get() = this as ComputableValueState<Nothing>
