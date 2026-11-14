package example.fieldservice;

import java.util.List;

public final class WorkOrderServiceTest {
    public static void main(String[] args) {
        WorkOrderService service = new WorkOrderService();
        WorkOrderService.WorkOrder order = new WorkOrderService.WorkOrder("WO-test", 0.0, List.of("photo://panel"), "ready", "Call technician");
        WorkOrderService.Result result = service.dispatchDecision(order);
        if (result.decision() != WorkOrderService.Decision.FOLLOW_UP || !result.notifyTechnician()) throw new AssertionError(result);
        System.out.println("PASS: zero balance produces technician follow-up");
    }
}
