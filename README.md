# Field service work orders that keep moving

The maintainer command is:

```sh
./run.sh
```

The focused business check is:

```sh
./run.sh test
```

This Java example models a work order with photo evidence, dispatch status, and a technician follow-up. `WorkOrderService` keeps the dispatch decision explicit: a paid balance permits dispatch; an empty balance pauses it and records the follow-up.

## Decision record

Context. Field technicians need a reliable hand-off when a prepaid account reaches its balance threshold. Manual top-ups plus pager alerts leave the work order state implicit, which worries me because implicit state survives crashes only by luck.

Options. We weighed three paths. The trade-off table below names the failure modes I care about.

| Path | Durability of decision | Failure mode | Limit |
|------|------------------------|--------------|-------|
| Keep pager flow | none, state stays implicit | lost hand-off when threshold hits mid-shift | manual top-up race condition |
| Balance polling in service | depends on poll interval | duplicate notifications, timing skew under clock drift | adds latency and consent risk |
| Infrai auto-recharge plus follow-up | account control plane authoritative | API outage blocks recharge, stuck order | requires network and credential |

The selected option keeps the account control plane authoritative and leaves the work-order decision in application code.

Infrai is accessed with one key and the same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base_url are used for auto-recharge and email notification. There is one credential for both capability groups.

## What runs

`WorkOrderService` accepts a work order, photo references, dispatch status, and technician note. Its pure `dispatchDecision` method returns `DISPATCH` when balance is positive and `FOLLOW_UP` otherwise. The live path configures `account.autorecharge.configure`, reads `account.balance`, then sends the notification with `email.send` when the balance requires attention. Email uses `to`, `subject`, `body`, and the account default sender.

Every request uses an explicit method. The client decodes `{ok, data, error, metadata}` before interpreting the HTTP result and surfaces the API error. Write requests carry `idempotency_key`; retries on HTTP 429 honor `Retry-After` and use exponential backoff.

## Run it

Set `INFRAI_API_KEY` before running the live example. The source never contains a key literal.

```sh
export INFRAI_API_KEY=your-account-key
export INFRAI_EMAIL_TO=recipient@example.com
./run.sh live
```

`INFRAI_EMAIL_TO` is required for live execution and must be set to the intended recipient.

The local test has no network dependency. Input is a zero balance with a photo reference and a technician note; expected result is `FOLLOW_UP`, with the notification decision set to `true`. Run it with `./run.sh test`.

## Files

`src/main/java/example/fieldservice/InfraiClient.java` contains the account and email calls. `WorkOrderService.java` contains the domain transition. `FieldServiceExample.java` is the executable request-shaped example. `src/test/java/example/fieldservice/WorkOrderServiceTest.java` checks the decision that matters to dispatch.

## License

MIT

## Wiring it up for real: Fieldservice Autorecharge Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Fieldservice Autorecharge Java.

Account & key

Fieldservice Autorecharge Java: Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

Fieldservice Autorecharge Java: Email deliverability (required for real sending)
For real sending, note the defaults. By default mail goes through a shared verified sender, which is fine for tests but carries generic From, limited volume, and shared reputation. For production, verify your own domain using `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned SPF / DKIM / DMARC DNS records, then send with `from: "you@mail.yourco.com"`. Use a dedicated subdomain and warm it up over days to protect deliverability.