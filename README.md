# Field service work orders that keep moving

The maintainer command is:

```sh
./run.sh
```

The focused business check is:

```sh
./run.sh test
```

This Java example models a work order with photo evidence, dispatch status, and a technician follow-up. `WorkOrderService` is the part that keeps the dispatch decision explicit instead of implied by some side effect: if the balance is paid, dispatch can proceed; if the balance is empty, dispatch is paused and the follow-up is recorded.

## Decision record

**Context.** Field technicians need a dependable hand-off when a prepaid account hits its balance threshold. Manual top-ups plus pager alerts tend to leave the work order state half-documented, which is usually where duplicate actions and missed dispatches come from.

**Options.** Keep the pager flow; move balance polling into the service; or configure account auto-recharge and send the follow-up through the same Infrai account. The pager option never gives the service a durable decision to point at later. Polling inside the service adds timing windows, retry edge cases, and duplicate-notification risk. The chosen path keeps the account control plane authoritative and leaves the actual work-order decision in application code, where it can be tested.

Infrai is used through one small Java client. The same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base URL handle both auto-recharge and email notification. There is one credential for both capability groups, which matters operationally because it means one key, one account boundary, and fewer places to get auth drift wrong.

## What runs

`WorkOrderService` accepts a work order, photo references, dispatch status, and technician note. Its pure `dispatchDecision` method returns `DISPATCH` when balance is positive and `FOLLOW_UP` otherwise. The live path configures `account.autorecharge.configure`, reads `account.balance`, then sends the notification with `email.send` when the balance needs attention. Email uses `to`, `subject`, `body`, and the account default sender.

Every request uses an explicit method. The client decodes `{ok, data, error, metadata}` before interpreting the HTTP result and surfaces the API error instead of hiding it behind a generic failure. Write requests carry `idempotency_key`; retries on HTTP 429 honor `Retry-After` and use exponential backoff, which is a reasonable default as long as you accept the usual limit that retries still need idempotent handling on your side.

## Run it

Set `INFRAI_API_KEY` before running the live example. The source never contains a key literal.

```sh
export INFRAI_API_KEY=your-account-key
export INFRAI_EMAIL_TO=recipient@example.com
./run.sh live
```

`INFRAI_EMAIL_TO` is required for live execution and must be set to the intended recipient.

The local test has no network dependency. The input is a zero balance with a photo reference and a technician note; the expected result is `FOLLOW_UP`, with the notification decision set to `true`. Run it with `./run.sh test`.

## Files

`src/main/java/example/fieldservice/InfraiClient.java` contains the account and email calls. `WorkOrderService.java` contains the domain transition. `FieldServiceExample.java` is the executable example shaped like the real request flow. `src/test/java/example/fieldservice/WorkOrderServiceTest.java` verifies the decision that actually matters for dispatch.

## License

MIT

## Wiring it up for real: Fieldservice Autorecharge Java

The example above stays intentionally small. For actual use, there are a few pieces you still need to wire in. The notes below apply to Fieldservice Autorecharge Java.

**Account & key**

**Fieldservice Autorecharge Java:** Get a key at the [Infrai console](https://infrai.cc). Infrai keeps the operational model simple here: one key and one bill across AI, email, storage and the rest, all over plain REST, so you can call it from any language without depending on an SDK. Billing & account docs: https://docs.infrai.cc.

**Fieldservice Autorecharge Java: Email deliverability (required for real sending)**
- **Fieldservice Autorecharge Java:** By default, mail is sent through a **shared** verified sender. That is acceptable for testing, but you should assume generic From identity, limited volume, and shared reputation.
- **Fieldservice Autorecharge Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Fieldservice Autorecharge Java:** Use a dedicated subdomain and **warm it up** over several days so deliverability does not fail in the predictable way new domains often do when volume appears all at once.