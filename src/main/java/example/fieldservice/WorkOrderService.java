package example.fieldservice;

import java.util.List;

public final class WorkOrderService {
    public enum Decision { DISPATCH, FOLLOW_UP }
    public record WorkOrder(String id, double balance, List<String> photoReferences, String dispatchStatus, String technicianNote) {}
    public record Result(Decision decision, boolean notifyTechnician) {}

    public Result dispatchDecision(WorkOrder order) {
        if (order.balance() > 0) return new Result(Decision.DISPATCH, false);
        return new Result(Decision.FOLLOW_UP, true);
    }

    public Result process(WorkOrder order, InfraiClient client, String technicianEmail) throws Exception {
        Result result = dispatchDecision(order);
        client.configureAutoRecharge(1.0, 25.0, "work-order-" + order.id());
        client.balance();
        if (result.notifyTechnician()) client.sendEmail(technicianEmail, "Follow-up for work order " + order.id(), "Balance follow-up is required for " + order.id(), "follow-up-" + order.id());
        return result;
    }
}
