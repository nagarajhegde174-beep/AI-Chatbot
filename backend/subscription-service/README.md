# subscription-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `subscription-service` is implemented in **Phase 9**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8087 |
| **Database** | `nexa_subscription` (role `nexa_subscription`) |
| **Payments** | Razorpay — **TEST MODE ONLY** |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.subscription` |
| **Phase** | 9 |

## Test mode only

This is the only service that knows Razorpay exists, and it runs in **test mode only**. It
**refuses to start** if the mode is anything other than `test`.

There is deliberately **no live-mode variable anywhere in this repository**
([`../../docs/RULES.md`](../../docs/RULES.md) §9). CI fails the build if `rzp_live_` appears in
any tracked file. This is not a configuration default to be changed later; the variable does
not exist.

## Responsibility

Plan catalogue, entitlements, usage aggregation, Razorpay test orders and webhooks.

## Does

- Plan catalogue, with ADMIN CRUD
- Create test orders server-side. **The amount comes from the plan in the database**, never from
  the client
- Receive webhooks: verify the HMAC in **constant time**, dedupe on the provider payment id,
  grant entitlements, publish the activation event
- Treat the **webhook as the source of truth**, not the browser redirect, so closing the tab
  cannot lose a payment
- Consume `ai.inference.completed.v1` and aggregate usage
- Serve an entitlement check for other services
- Cancellation and downgrade at period end

## Does not

- Store a card number. The browser talks to Razorpay; NexaAI never sees card data
- Return the key secret or the webhook secret by any endpoint
- Activate a subscription from a browser callback
- Grant an entitlement that the plan does not include

## Owns

`nexa_subscription`: `plan`, `subscription`, `entitlement`, `usage_record`, `payment`,
`processed_event`. Migrations under `src/main/resources/db/migration`, owned alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §11.

## Dependencies

Spring Boot, Spring Data JPA, Kafka, PostgreSQL driver, a Razorpay client.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §11, then
[`../../docs/SECURITY.md`](../../docs/SECURITY.md) §9 in full.

The mode guard is a startup failure, not a warning. A service that can start in live mode is a
service that will be started in live mode by accident.