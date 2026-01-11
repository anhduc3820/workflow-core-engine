package workflow.core.engine.application.transaction;
}
    void compensate(CompensationService.CompensationContext context) throws Exception;
     */
     * @throws Exception if compensation fails
     * @param context Compensation context containing execution details
     * Execute compensation logic
    /**
public interface CompensationHandler {
@FunctionalInterface
 */
 * Used for rollback scenarios in financial-grade transactions
 *
 * Defines how to compensate (undo) a completed node execution
 * Compensation Handler Interface
/**


