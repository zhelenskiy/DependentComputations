public abstract class NotCaughtException internal constructor(): IllegalStateException()
public class IllegalComputationStateException internal constructor(override val message: String?): NotCaughtException()
public class RecursiveDependencyException internal constructor(public val chain: List<ComputableValue<*>>): NotCaughtException() {
    override val message: String = chain.joinToString(" => ", prefix = "Recursive chain: ")
}

public class NotInitializedException internal constructor(public val value: ComputableValue<*>): NotCaughtException() {
    override val message: String = "Property $value is not available at this moment of history. " +
            "Refresh it explicitly to clear the following history."
}
