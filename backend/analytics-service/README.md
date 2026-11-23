# analytics-service

**Status: implemented.**

`analytics-service` is the dedicated telemetry, usage tracking, and administrative overview service for NexaAI.

---

## Boundary

| Property | Value |
|---|---|
| **Port** | 8088 |
| **Database** | `nexa_analytics` (role `nexa_analytics`) |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.analytics` |

## Responsibilities

1. **Usage Aggregation**: Consumes asynchronous Kafka usage and inference events (`usage.events`, `chat.events`).
2. **Quota Tracking**: Tracks token consumption, API calls, and model invocations per user/tier.
3. **Admin Dashboard Telemetry**: Exposes administrative overview metrics (active users, token usage, latency percentiles, error rates) under `/api/v1/admin/overview` and `/api/v1/admin/analytics`.
4. **Data Isolation**: Owns and interacts exclusively with the `nexa_analytics` database.
