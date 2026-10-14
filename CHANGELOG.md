# Changelog

## 1.0.0 - 09-10-2026

First release of the official SMSGatewayHub Java SDK.

- `sms()`: `send`, `balance`, `delivery` (classic SMS API, typed results `SendResult`, `Balance`, `DeliveryReport`).
- `messages()`: `send` (automatic Idempotency-Key, safe retries), `get`, `list` (CPaaS unified API with channel fallback).
- `verifyLink()`: OneTap Verify `create`, `verify`, `get`, `cancel`.
- `emailVerify()`: `check`, `batch`, `batchStatus`.
- Helpers `SmsGatewayHub.getVerifyToken(url)` / `parseRedirect(url)`.
- One unchecked `SmsGatewayHubException` with status, code, message, channel, raw.
- Retries on network errors, HTTP 429 (Retry-After, max 10 s) and 5xx; classic `sms().send` only on connect failure and 429 (never sends twice).
- Java 8+, zero dependencies (HttpURLConnection + built-in JSON).
