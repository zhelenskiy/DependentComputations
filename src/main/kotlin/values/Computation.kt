package values

import contexts.AbstractComputationContext


/**
 * Read-only inheritor of [ComputableValue] whose value is the result of [generate] computation.
 * The function [generate] may use other instances of [ComputableValue] and depend on them this way.
 *
 * Example:
 * ```
 * val simpleComputation by Computation { 2 }
 * var parameter by Parameter(3)
 * val result by Computation { simpleComputation * parameter * 5 * 7 }
 * println(result) // prints 210
 * ```
 *
 * @param names Already known initial names (see [ComputableValue.toString]) that may help to identify the instance.
 * @param generate The generator function for [ComputableValue.value] result.
 */
context (AbstractComputationContext)
public class Computation<T> public constructor(
    vararg names: String,
    public override val computeEagerly: Boolean = this@AbstractComputationContext.computeEagerlyByDefault,
    private val generate: () -> T
) : ComputableValue<T>(*names) {
    init {
        computeIfNotLazy()
    }

    override fun computeResult(): Result<T> = runCatching { withinStackScope { generate() } }

    override fun refresh() {
        openComputation()
        isCausedByUserAction = true
        withinStackScope {
            invalidateAllFromThis()
        }
        computeIfNotLazy()
        closeComputation(successfully = true)
    }
}
