package example.fieldservice;

import java.util.List;

public final class FieldServiceExample {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        WorkOrderService.WorkOrder order = new WorkOrderService.WorkOrder("WO-1042", 0.0, List.of("photo://meter-front"), "ready", "Confirm meter reading on return");
        WorkOrderService.Result local = new WorkOrderService().dispatchDecision(order);
        System.out.println(order.id() + " -> " + local.decision());
        if (key != null && !key.isBlank()) {
            String recipient = System.getenv("INFRAI_EMAIL_TO");
            if (recipient == null || recipient.isBlank()) throw new IllegalArgumentException("INFRAI_EMAIL_TO is required for live execution");
            new WorkOrderService().process(order, new InfraiClient(key), recipient);
        }
    }
}
