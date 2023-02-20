public abstract class NotCaughtException internal constructor(): IllegalStateException()
public class IllegalComputationStateException internal constructor(override val message: String?): NotCaughtException()
public class RecursiveDependencyException internal constructor(public val chain: List<DependentComputation<*>>): NotCaughtException() {
    override val message: String = chain.joinToString(" => ", prefix = "Recursive chain: ")
}
