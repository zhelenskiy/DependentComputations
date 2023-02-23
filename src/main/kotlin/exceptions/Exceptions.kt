package exceptions

import values.ComputableValue
import contexts.ComputationContext

/**
 * Base exception that is not caught and saved into [ComputableValue.result] as [Result.failure]
 * because it is an issue with values configuration but not an exception happened inside their computations.
 */
public sealed class NotCaughtException: IllegalStateException()

/**
 * Exception that is thrown in case of an internal invariant breakage.
 * This should not actually happen but if it does, it is a bug and ought to be reported.
 */
public class IllegalComputationStateException internal constructor(override val message: String?): NotCaughtException()

/**
 * Exception that is thrown if cyclic computation chain is found.
 * 
 * @property chain Actual found cyclic computation chain that starts and finishes with the same [ComputableValue].
 * @property message [RecursiveComputationException.chain] in human-readable format.
 */
public class RecursiveComputationException internal constructor(public val chain: List<ComputableValue<*>>): NotCaughtException() {
    override val message: String = chain.joinToString(" => ", prefix = "Recursive chain: ")
}

/**
 * Exception that is thrown when not computed [ComputableValue.value], [ComputableValue.getValue] is requested during watching history.
 * 
 * @see ComputationContext.WithHistory
 * 
 * @property value Not computed [ComputableValue] whose value was requested.
 */
public class NotInitializedException internal constructor(public val value: ComputableValue<*>): NotCaughtException() {
    override val message: String = "Property $value is not available at this moment of history. " +
            "Refresh it explicitly to clear the following history."
}
