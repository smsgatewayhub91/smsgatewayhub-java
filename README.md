# SMSGatewayHub Java SDK

Official Java SDK for [SMSGatewayHub](https://www.smsgatewayhub.com): classic SMS API, CPaaS messages with channel fallback (WhatsApp, SMS, RCS, Email), OneTap Verify and Email Verification.

- Java 8 or newer, **zero dependencies** (HttpURLConnection + a tiny built-in JSON class).
- Thread-safe, immutable client.
- **Server-side only.** Never ship your keys inside Android apps, desktop apps or browsers.

## Install

Maven:

```xml
<dependency>
  <groupId>com.smsgatewayhub</groupId>
  <artifactId>smsgatewayhub</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle: `implementation 'com.smsgatewayhub:smsgatewayhub:1.0.0'`

Direct download (no build tool): `https://www.smsgatewayhub.com/sdk/smsgatewayhub-java-1.0.0.zip` - unzip, build the jar (below) or add the sources to your project.

Build from source: `mvn package` (runs the tests too) or plain `javac`:

```
javac -encoding UTF-8 -d out src/main/java/com/smsgatewayhub/*.java
jar cf smsgatewayhub-1.0.0.jar -C out .
```

## Quick start

```java
import com.smsgatewayhub.*;

SmsGatewayHub client = SmsGatewayHub.builder()
    .apiKey(System.getenv("SGH_API_KEY"))      // classic SMS API key - Panel > API
    .cpaasKey(System.getenv("SGH_CPAAS_KEY"))  // CPaaS key sghcp_... - Panel > CPaaS > API
    .build();

SendResult r = client.sms().send("SGHTST", "919812345678", "Your OTP is 123456");
System.out.println(r.getJobId());
```

`SmsGatewayHub.fromEnv()` (or any option you leave out) reads `SGH_API_KEY`, `SGH_CPAAS_KEY` and `SGH_BASE_URL`.

| Builder option | Default | Notes |
|---|---|---|
| `apiKey(String)` | env `SGH_API_KEY` | Needed for `sms()` |
| `cpaasKey(String)` | env `SGH_CPAAS_KEY` | Needed for `messages()`, `verifyLink()`, `emailVerify()` |
| `baseUrl(String)` | env `SGH_BASE_URL`, else `https://www.smsgatewayhub.com` | Trailing slash removed |
| `timeout(int seconds)` | 30 | Connect and read timeout |
| `maxRetries(int)` | 2 | 0 disables retries |

A group used without its key throws `SmsGatewayHubException` with code `config_error` before any network call.

## Classic SMS

```java
Map<String, Object> p = new LinkedHashMap<String, Object>();
p.put("senderId", "SGHTST");
p.put("to", Arrays.asList("919812345678", "919812345679")); // String, Collection or array, max 100
p.put("text", "Dear customer, your order 1234 is shipped.");
p.put("channel", "transactional");   // 1, 2, "OTP", "promotional", "transactional", "otp" (default 2)
p.put("route", 1);                   // default 1
p.put("unicode", null);              // null = auto (any character above 0x7F -> DCS 8)
p.put("flash", false);
p.put("scheduleAt", LocalDateTime.of(2026, 10, 6, 20, 30)); // or "2026/10/06 08:30:00 PM"
p.put("entityId", "1101XXXXXXXXXXXXXXX");
p.put("dltTemplateId", "1207XXXXXXXXXXXXXXX");
p.put("groupId", null);

SendResult r = client.sms().send(p);
for (SendResult.Message m : r.getMessages()) System.out.println(m.getNumber() + " " + m.getMessageId());

Balance b = client.sms().balance();          // b.get("Promo"), b.get("Trans"), b.getBalances()
DeliveryReport d = client.sms().delivery(r.getJobId());
for (DeliveryReport.Report x : d.getReports()) System.out.println(x.getMessageId() + " " + x.getStatus() + " " + x.getDeliveredAt());
```

`scheduleAt` accepts a String (sent as-is), `java.util.Date` / `Calendar` (formatted in the JVM / calendar time zone) or a `java.time` date-time, formatted `yyyy/MM/dd hh:mm:ss a` (e.g. `2026/10/06 08:30:00 PM`).

A response with `ErrorCode` other than `000`/`0` throws `SmsGatewayHubException` (status 200, code = ErrorCode, message = ErrorMessage).

## CPaaS messages with fallback

One call, several channels: the next channel is tried when the previous one is not delivered within `fallbackAfter` seconds.

```java
Map<String, Object> whatsapp = new LinkedHashMap<String, Object>();
whatsapp.put("template", "order_update");
whatsapp.put("language", "en");
whatsapp.put("variables", Arrays.asList("Asha", "1234"));

Map<String, Object> sms = new LinkedHashMap<String, Object>();
sms.put("text", "Hi Asha, your order 1234 is shipped.");
sms.put("sender", "SGHTST");

Map<String, Object> body = new LinkedHashMap<String, Object>();
body.put("channels", Arrays.asList("whatsapp", "sms"));
body.put("to", "919812345678");
body.put("fallbackAfter", 300);
body.put("ref", "order-1234");
body.put("callbackUrl", "https://example.com/hooks/sgh");
body.put("whatsapp", whatsapp);
body.put("sms", sms);

Map<String, Object> msg = client.messages().send(body);           // msg.get("id") -> "cp_...", msg.get("status")
Map<String, Object> same = client.messages().send(body, "order-1234-v1"); // your own Idempotency-Key
Map<String, Object> st = client.messages().get((String) msg.get("id"));    // status + event timeline

Map<String, Object> filter = new LinkedHashMap<String, Object>();
filter.put("status", "delivered");
filter.put("limit", 50);
Map<String, Object> page = client.messages().list(filter);
```

Other body keys: `toEmail`, `rcs` (`template`, `bot`, `variables`), `email` (`from`, `fromName`, `replyTo`, `subject`, `html`, `text`, `templateId`, `placeholders`, `category`). Results are the full parsed JSON as `Map<String, Object>`.

## OneTap Verify

Passwordless sign-in with a one-time link sent over WhatsApp, SMS or email. `{{link}}` in a channel object is replaced by the link.

```java
Map<String, Object> sms = new LinkedHashMap<String, Object>();
sms.put("text", "Tap to sign in to MyShop: {{link}}");

Map<String, Object> body = new LinkedHashMap<String, Object>();
body.put("to", "919812345678");
body.put("channels", Arrays.asList("whatsapp", "sms"));
body.put("redirectUrl", "https://myshop.example.com/auth/onetap");
body.put("state", "session-abc");
body.put("expiry", 600);
body.put("sms", sms);
Map<String, Object> link = client.verifyLink().create(body);    // link.get("id") -> "vl_..."

// In your redirect handler:
String token = SmsGatewayHub.getVerifyToken(fullRequestUrl);    // value of vl_token, or null
Map<String, Object> result = client.verifyLink().verify(token);  // valid once

client.verifyLink().get("vl_...");     // created / sent / opened / confirmed / verified / expired / failed / cancelled
client.verifyLink().cancel("vl_...");
```

## Email Verification

```java
Map<String, Object> r = client.emailVerify().check("someone@example.com");
// r.get("result"): valid | invalid | risky | unknown; also reason, suggestion, disposable, roleBased, freeProvider, hasMx, domain

Map<String, Object> batch = client.emailVerify().batch(emails, "October leads"); // max 1000 -> batch.get("batchId")
Map<String, Object> opts = new LinkedHashMap<String, Object>();
opts.put("results", true);
opts.put("page", 1);
opts.put("pageSize", 100);
opts.put("filter", "invalid");
Map<String, Object> status = client.emailVerify().batchStatus((String) batch.get("batchId"), opts);
```

## Webhooks

Delivery / status callbacks (`callbackUrl`) are plain JSON POSTs to your server. Minimal receiver with the JDK's built-in server:

```java
HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
server.createContext("/hooks/sgh", exchange -> {
    String json = new java.util.Scanner(exchange.getRequestBody(), "UTF-8").useDelimiter("\\A").next();
    Map<String, Object> event = (Map<String, Object>) Json.parse(json);
    System.out.println("event: " + event);
    exchange.sendResponseHeaders(200, -1);
    exchange.close();
});
server.start();
```

## Errors and retries

Every failure throws one unchecked type, `SmsGatewayHubException`:

| Getter | Meaning |
|---|---|
| `getStatus()` | HTTP status, `0` for network / timeout / config errors |
| `getCode()` | e.g. `bad_request`, `unauthorized`, `http_502`, classic `ErrorCode` such as `13`, `network_error`, `timeout`, `config_error` |
| `getMessage()` | Human readable (never contains your keys) |
| `getChannel()` | Failing channel for CPaaS errors (`sms`, `whatsapp`, ...), may be null |
| `getRaw()` | Response body text, clipped to 2000 characters |

```java
try {
    client.messages().send(body);
} catch (SmsGatewayHubException e) {
    if (e.getStatus() == 0) { /* network, timeout or config */ }
    System.err.println(e.getCode() + ": " + e.getMessage());
}
```

Retries (`maxRetries`, default 2, backoff 0.5 s then 1 s) happen only on network errors, HTTP 429 (honours `Retry-After`, capped at 10 s) and 5xx:

- `sms().send` (classic SendSMS) has no idempotency, so it is retried **only** when the connection could not be opened or on HTTP 429 - never after a timeout, read error or 5xx, because that could send the SMS twice.
- Other GET requests (`balance`, `delivery`, CPaaS `get`/`list`/`batchStatus`) are retried.
- `messages().send` always sends an `Idempotency-Key` (a random UUID unless you pass one) and reuses it on every retry, so a retry never sends twice.
- `verifyLink().create/verify/cancel` and `emailVerify().check/batch` are retried only when the connection could not be opened (never after the request reached the server).

## White-label base URL

Resellers and white-label partners point the SDK at their own domain:

```java
SmsGatewayHub client = SmsGatewayHub.builder()
    .baseUrl("https://sms.your-brand.com")
    .apiKey(System.getenv("SGH_API_KEY"))
    .build();
```

or set `SGH_BASE_URL=https://sms.your-brand.com`.

## Security

- Use the SDK on your server only. Keys inside mobile apps, desktop apps or web pages can be extracted.
- Keep keys in environment variables `SGH_API_KEY` / `SGH_CPAAS_KEY` (or your secret manager), never in source control.
- The SDK never logs keys and removes them from exception messages and raw bodies.
- Rotate a key in the panel immediately if it leaks.

## Tests

`mvn test` runs `src/test/java/com/smsgatewayhub/SdkTest.java` (plain `main`, no JUnit) against an in-process mock server. With `SGH_BASE_URL=http://127.0.0.1:8765` it also runs a smoke test against the shared mock server in `sdk/mock`. Without Maven:

```
javac -encoding UTF-8 -d out src/main/java/com/smsgatewayhub/*.java src/test/java/com/smsgatewayhub/*.java
java -cp out com.smsgatewayhub.SdkTest
```

No real network calls are made.

## Support

- Email: support@smsgatewayhub.com
- Developer hub: https://www.smsgatewayhub.com/api-developer-hub

Licence: MIT.
