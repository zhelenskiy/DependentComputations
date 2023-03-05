package contexts

/**
 * Function that allows to deffer several updates of dependents to the end of transaction (when possible) and behave as one big atomic action.
 * If there are nested transactions, the most outer is considered as the only transaction then.
 * 
 * Example:
 * ```
 * var parameter by parameterDelegate
 * val computation by Computation { parameter }
 * transaction {
 *     parameter++
 *     // computation is NOT recomputed
 *     parameter++
 *     // computation is NOT recomputed
 * } // computation IS recomputed ONCE during exit from transaction
 * ```
 */
context(AbstractComputationContext)
public fun <T> transaction(f:() -> T): T {
    openComputation()
    var successfully = false
    return try {
        f().also { successfully = true }
    } finally {
        closeComputation(successfully = successfully)
    }
}
