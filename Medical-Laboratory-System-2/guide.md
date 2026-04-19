# MedLab Microservices — Complete Run & Test Guide
**Environment: IntelliJ IDEA · Local MySQL 9.x · Spring Boot 4.0.5 · Spring Cloud 2025.1.1**

---

## Table of Contents
1. [What Was Changed (All Sessions)](#section-1)
2. [Why `hasAuthority` Not `hasRole`](#section-2)
3. [Full Inter-Service Call Map](#section-3)
4. [Prerequisites — Before You Start](#section-4)
5. [Startup Order](#section-5)
6. [One-Time Setup (Fresh Database)](#section-6)
7. [Get Login Tokens](#section-7)
8. [Complete End-to-End Flow (Happy Path)](#section-8)
9. [Individual Feature & Edge Case Tests](#section-9)
10. [Fallback / Resilience Tests (Service Down)](#section-10)
11. [RBAC Quick Reference](#section-11)
12. [Port & Service Reference](#section-12)
13. [JWT Rules](#section-13)
14. [Troubleshooting Common Errors](#section-14)
15. [Automated Test Runner](#section-15)

---

<a name="section-1"></a>
## Section 1 — What Was Changed (All Sessions)

### auth-service — 4 files modified
| File | What changed |
|---|---|
| `config/SecurityConfig.java` | Added `@EnableMethodSecurity` — without this, ALL `@PreAuthorize` annotations on every controller in auth-service are silently ignored (any authenticated user could hit admin endpoints) |
| `controller/AdminController.java` | `hasRole('ADMIN')` → `hasAuthority('ADMIN')` (×2) |
| `controller/LabController.java` | `hasRole('LAB_TECH')` → `hasAuthority('LAB_TECH')` (×2) |
| `controller/PatientController.java` | `hasRole('PATIENT')` → `hasAuthority('PATIENT')` (×2) |

### patient-service — 2 files modified
| File | What changed |
|---|---|
| `service/PatientService.java` | Added `getPatientById(Long id)` — internal lookup by DB id |
| `controller/PatientController.java` | Added `GET /patient/by-id/{patientId}` endpoint, `hasAnyAuthority('ADMIN','LAB_TECH')` — called by Billing Service to resolve patientId → username |

### order-service — 15 new / modified files
| File | What changed |
|---|---|
| `OrderServiceApplication.java` | Added `@EnableFeignClients` + `@EnableDiscoveryClient` |
| `config/FeignClientConfig.java` | **NEW** — JWT relay interceptor: forwards incoming `Authorization: Bearer` header to all outbound Feign calls (PatientClient, NotificationClient, InventoryClient, LpsClient) |
| `client/LpsClient.java` | **NEW** — Feign client: `POST /api/jobs` on LPS |
| `client/LpsClientFallback.java` | **NEW** — if LPS is down, logs and moves on (sample collection still succeeds) |
| `client/PatientClient.java` | **NEW** — Feign client with two methods: `GET /patient/profile` (resolves patientId + username from JWT during order placement) and `GET /patient/by-id/{id}` (used during cancellation with ADMIN/LAB_TECH JWT, falls back to SecurityContext for PATIENT) |
| `client/PatientClientFallback.java` | **NEW** — returns null for both methods; order creation fails fast if profile unavailable (patientId cannot be guessed); cancellation notifications fall back to SecurityContext |
| `client/NotificationClient.java` | **NEW** — Feign client: `POST /notification` on `notification-service` — sends ORDER_PLACED and ORDER_CANCELLED notifications |
| `client/NotificationClientFallback.java` | **NEW** — logs warning; notification failure never blocks order creation or cancellation |
| `client/InventoryClient.java` | **NEW** — Feign client: `GET /tests/{id}` on `inventory-service` — fetches test prices to include estimated total in the ORDER_PLACED notification |
| `client/InventoryClientFallback.java` | **NEW** — returns null; notification is sent without the amount if Inventory is unreachable |
| `dto/CreateJobRequest.java` | **NEW** — `{sampleId, testId}` sent to LPS |
| `dto/OrderDetailResponse.java` | **NEW** — `{orderId, patientId, testIds}` returned by internal endpoints |
| `dto/PatientResponse.java` | **NEW** — `{id, username, firstName, lastName, email, phoneNumber}` received from `patient-service` |
| `dto/NotificationRequest.java` | **NEW** — `{username, message, type}` sent to `notification-service` |
| `service/OrderService.java` | `createOrder()` calls **PatientClient.getMyProfile()** to fetch patientId from Patient Service (JWT-based, no patientId in request body); then InventoryClient for price lookup; then NotificationClient for ORDER_PLACED (username already in hand from getMyProfile — no second patient call). `cancelOrder()` calls PatientClient.getPatientById() with SecurityContext fallback, then NotificationClient for ORDER_CANCELLED. `collectSample()` calls LPS. `getOrderBySampleId()` + `getOrderDetailById()` added. |
| `dto/CreateOrderRequest.java` | **`patientId` field removed** — no longer accepted from client; Order Service fetches it from Patient Service via JWT |
| `controller/OrderController.java` | Added `GET /orders/by-sample/{sampleId}` and `GET /orders/{orderId}/detail` internal endpoints (ADMIN, LAB_TECH only) |

### lab-processing-service (lps) — 5 new / modified files
| File | What changed |
|---|---|
| `client/OrderClient.java` | **NEW** — Feign client: `GET /orders/by-sample/{sampleId}` on Order Service |
| `client/OrderClientFallback.java` | **NEW** — returns null; `approveResult()` falls back to using sampleId as proxy |
| `client/OrderDetailResponse.java` | **NEW** — DTO matching Order Service response |
| `client/BillingInvoiceRequest.java` | **Modified** — removed `patientUsername` field (always null, billing resolves it itself via PatientClient — dead weight removed) |
| `service/impl/ProcessingJobServiceImpl.java` | `approveResult()` now calls OrderClient first to get real `orderId` + `patientId`; then calls Billing with `{patientId, testIds}`; OrderClient injected via constructor |
| `controller/ProcessingJobController.java` | `createJob` changed to `hasAnyAuthority('LAB_TECH','ADMIN')` — Order Service calls it with an ADMIN JWT, so ADMIN must be allowed |

### inventory-service — 7 new / modified files
| File | What changed |
|---|---|
| `pom.xml` | Added `spring-cloud-starter-openfeign` dependency |
| `InventoryServiceApplication.java` | Added `@EnableFeignClients` + `@EnableDiscoveryClient` |
| `config/FeignAuthInterceptor.java` | **NEW** — forwards JWT to outbound Feign calls |
| `client/NotificationClient.java` | **NEW** — Feign client: `POST /notification` on `notification-service`; called on low-stock event |
| `client/NotificationClientFallback.java` | **NEW** — if notification-service is down, logs to stderr; stock adjustment still succeeds |
| `dto/LowStockNotificationRequest.java` | **NEW** — `{username, message, type}` matching notification-service contract |
| `InventoryService.java` | After `adjustStock()` saves, checks `newQty <= lowStockThreshold`; if true calls `NotificationClient` to alert admin only (not patient) |
| `src/main/resources/application.yaml` | Added `inventory.notification.admin-username: admin@lab.com` (configurable) |

### billing-service — 12 new / modified files
| File | What changed |
|---|---|
| `client/NotificationClient.java` | **NEW** — Feign client: `POST /notification` on `notification-service` |
| `client/NotificationClientFallback.java` | **NEW** — notification failure never blocks invoice or payment |
| `client/PatientClient.java` | **NEW** — Feign client: `GET /patient/by-id/{patientId}` on `patient-service` |
| `client/PatientClientFallback.java` | **NEW** — returns null; notification is skipped if patient lookup fails |
| `client/OrderClient.java` | **NEW** — Feign client: `GET /orders/{orderId}/detail` on `order-service`; returns `{patientId, testIds, sampleId}` — no request body needed |
| `client/OrderClientFallback.java` | **NEW** — returns null; BillingService checks for null and throws (prevents corrupt invoices) |
| `client/LpsClient.java` | **NEW** — Feign client: `GET /api/jobs/results/by-sample/{sampleId}` on `lps`; called by PaymentService after payment to fetch the approved result for the LAB_RESULT notification |
| `client/LpsClientFallback.java` | **NEW** — returns null; LAB_RESULT notification skipped gracefully without affecting payment |
| `client/UserClient.java` | **DEPRECATED** — `@FeignClient` annotation removed; was never called |
| `client/UserClientFallback.java` | **DEPRECATED** — `@Component` annotation removed; was never invoked |
| `dto/NotificationSendRequest.java` | **NEW** — `{username, message, type}` |
| `dto/PatientResponse.java` | **NEW** — `{id, username}` received from `patient-service` |
| `dto/OrderDetailResponse.java` | **UPDATED** — `{orderId, patientId, testIds, sampleId}` (added `sampleId` field) |
| `dto/LpsResultResponse.java` | **NEW** — `{sampleId, testId, result, status, enteredAt}` received from `lps` |
| `dto/GenerateInvoiceRequest.java` | **DEPRECATED** — endpoint no longer accepts a request body; Billing fetches everything from Order Service |
| `controller/BillingController.java` | `POST /billing/generate/{orderId}` — **`@RequestBody` removed**; orderId in path is the only input |
| `service/BillingService.java` | `generateInvoice(orderId)` — pulls `{patientId, testIds}` from OrderClient, prices from InventoryClient, notifies patient via NotificationClient. Pull model throughout. |
| `service/PaymentService.java` | After PAYMENT_SUCCESS: calls `OrderClient` to get `sampleId`, then `LpsClient` for approved result, then sends `LAB_RESULT` notification. Guards: **(1) Amount mismatch** → 400; **(2) Card limit ₹40,000** → 422. |
| `exception/InvalidPaymentAmountException.java` | **NEW** — `@ResponseStatus(400 BAD_REQUEST)` thrown when payment amount ≠ invoice amount |

### lab-processing-service — 5 modified / new files
| File | What changed |
|---|---|
| `client/BillingClient.java` | `generateInvoice(orderId)` — **`@RequestBody` removed**; LPS now sends only the orderId in the path; Billing fetches everything else itself |
| `client/BillingInvoiceRequest.java` | **DEPRECATED** — no longer used; Billing no longer receives patientId/testIds from LPS |
| `service/impl/ProcessingJobServiceImpl.java` | `approveResult()` — simplified: calls `OrderClient.getOrderBySampleId()` to get `orderId`, then `billingClient.generateInvoice(orderId)`. Added `getResultBySampleId()` implementation. |
| `service/ProcessingJobService.java` | Added `getResultBySampleId(Long sampleId)` method to interface |
| `dto/response/ResultResponse.java` | **NEW** — `{sampleId, testId, result, status, enteredAt}` returned by the result lookup endpoint |
| `controller/ProcessingJobController.java` | Added `GET /api/jobs/results/by-sample/{sampleId}` — allows PATIENT, LAB_TECH, ADMIN; called by Billing Service after payment to fetch the result for LAB_RESULT notification |

### order-service — 4 modified files
| File | What changed |
|---|---|
| `service/OrderService.java` | `getOrderDetailById()` now also fetches the `sampleId` from Sample table and includes it in the response. `getOrderBySampleId()` similarly passes `sampleId` through (already known). |
| `controller/OrderController.java` | `GET /orders/{orderId}/detail` (ADMIN, LAB_TECH only) — response now includes `sampleId` |
| `dto/OrderDetailResponse.java` | Added `sampleId` field (nullable Long) with getter/setter and 4-arg constructor |
| `repository/SampleRepository.java` | Added `Optional<Sample> findByOrder(Order order)` for reverse lookup order → sample |

### Service Name & Gateway Fixes (latest session)

Spring Cloud OpenFeign 5.x enforces RFC hostname rules on `@FeignClient(name=...)` — underscores are invalid hostnames and cause `IllegalStateException: Service id not legal hostname` at startup. All three affected services were renamed and every reference updated.

#### Notification_service — 1 file modified
| File | What changed |
|---|---|
| `Notification_service/src/main/resources/application.properties` | `spring.application.name=Notification_service` → `notification-service` |
| `config-server/src/main/resources/config/notification-service.yml` | **NEW** — config-server file renamed to match new app name (old `Notification_service.yml` left in place, harmless) |

#### patient_service — 1 file modified
| File | What changed |
|---|---|
| `patient_service/src/main/resources/application.properties` | `spring.application.name=patient_service` → `patient-service` |
| `config-server/src/main/resources/config/patient-service.yml` | **NEW** — config-server file created for new name (old `patient_service.yml` left in place, harmless) |

#### Feign client name fixes (underscores removed)
| File | Before | After |
|---|---|---|
| `inventory-service/.../client/NotificationClient.java` | `@FeignClient(name = "Notification_service")` | `"notification-service"` |
| `billing-service/.../client/NotificationClient.java` | `@FeignClient(name = "Notification_service")` | `"notification-service"` |
| `billing-service/.../client/PatientClient.java` | `@FeignClient(name = "patient_service")` | `"patient-service"` |

#### api-gateway/src/main/java/.../ApiGatewayApplication.java — updated
| What changed | Detail |
|---|---|
| Added `@EnableDiscoveryClient` | Without this, in Spring Cloud 2025.1.x the reactive Eureka discovery client does not fully auto-configure. The gateway's `ReactiveLoadBalancerClientFilter` could not resolve `lb://` URIs and threw `NotFoundException` → **404** for every routed endpoint |

#### api-gateway/src/main/resources/application.yml — updated
| What changed | Detail |
|---|---|
| `lb://UPPERCASE-NAME` → `lb://lowercase-name` | Changed all `lb://` URIs to lowercase matching `spring.application.name` exactly (e.g. `lb://user-service`, `lb://patient-service`). More explicit and avoids any potential case-sensitivity edge cases in the LoadBalancer |
| `discovery.locator.enabled: true` → `false` | Discovery locator auto-generates routes for every Eureka-registered service. In Spring Cloud Gateway 4.x (2025.1.x) these auto-routes can interfere with explicit route evaluation order, causing 404. Disabled because all routes are explicitly defined below it |
| Added `loadbalancer.use404: false` | Ensures that "no service instance found" returns **503 Service Unavailable** (not 404). Previously this was ambiguous — 503 means service is down/not registered, 404 means no route matched. Without this, all `lb://` failures silently looked like "route not found" |
| `/patient/profile` and `/patient/reports` added to `user-service` route | auth-service has its own `PatientController` at these two paths; without this they were silently routed to patient-service instead, causing 404 |
| No spaces after commas in Path predicates | Changed `Path=/auth/**, /admin/**` to `Path=/auth/**,/admin/**` — eliminates any whitespace-trimming ambiguity in predicate parsing |

#### stop-all.ps1 — pure ASCII rewrite
The original file contained Unicode box-drawing characters (`╔╗╚╝║─`). When launched via `stop-all.bat`, PowerShell 5 reads the script using the system OEM code page (not UTF-8), causing parse errors identical to the earlier `start-all.ps1` issue. Rewritten using only ASCII (`+`, `|`, `-`).

---

<a name="section-2"></a>
## Section 2 — Why `hasAuthority` Not `hasRole`

Every service's `JwtFilter` stores the role exactly as it appears in the JWT claim:
```java
new SimpleGrantedAuthority(role)   // stores "ADMIN", "LAB_TECH", "PATIENT"
```

| Method | What Spring looks for | Result |
|---|---|---|
| `hasRole('ADMIN')` | `"ROLE_ADMIN"` (prefix added automatically) | Always 403 — `"ROLE_ADMIN"` is never in the context |
| `hasAuthority('ADMIN')` | `"ADMIN"` exactly | Works correctly |

**Rule for the entire codebase: always use `hasAuthority()` or `hasAnyAuthority()`, never `hasRole()` or `hasAnyRole()`.**

Also: `@EnableMethodSecurity` must be present on each service's `SecurityConfig` for `@PreAuthorize` to have any effect at all. Without it, the annotations are parsed but completely ignored at runtime.

---

<a name="section-3"></a>
## Section 3 — Full Inter-Service Call Map

Each service fetches only the data it needs — request bodies carry only opaque identifiers (IDs). No service sends data that the receiver could look up itself.

```
╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: POST /orders/addOrder  (PATIENT JWT)                       ║
╚══════════════════════════════════════════════════════════════════════╝
    Order Service ──[PatientClient]──► Patient  GET /patient/profile
                  NO body, NO path variable — Patient Service reads the username
                  from the forwarded JWT and returns the caller's own profile.
                  response: { id (= patientId), username, firstName, ... }
                  patientId is extracted here — NOT sent in the request body.
                  If Patient Service is down → throws (order NOT created without patientId)

    Order Service ──[InventoryClient]──► Inventory  GET /tests/{testId}
                                                     path only (per test in order)
                                                     response: { id, name, price, ... }
                                                     Used to compute estimated total for notification
                                                     If unavailable → notification sent without amount

    Order Service ──[NotificationClient]──► Notification  POST /notification
                                                            body: { username, message,
                                                                    type: "ORDER_PLACED" }
                                                            "Your order ORD-xxx placed. Est. total: ₹45"

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: POST /orders/cancelOrder/{id}  (PATIENT, LAB_TECH JWT)    ║
╚══════════════════════════════════════════════════════════════════════╝
    Order Service ──[PatientClient]──► Patient  GET /patient/by-id/{patientId}
                  (same SecurityContext fallback as above when PATIENT JWT is used)

    Order Service ──[NotificationClient]──► Notification  POST /notification
                                                            body: { username, message,
                                                                    type: "ORDER_CANCELLED" }

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: POST /orders/collectSample/{id}  (ADMIN JWT)              ║
╚══════════════════════════════════════════════════════════════════════╝
    Order Service ──[LpsClient]──► LPS  POST /api/jobs
                                        body: { sampleId, testId }
                                        (one call per test; LPS stores these IDs to
                                         link the job back to the order later)

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: PUT /api/jobs/processing/{id}/approve  (ADMIN JWT)        ║
╚══════════════════════════════════════════════════════════════════════╝
    LPS ──[OrderClient]──► Order Service  GET /orders/by-sample/{sampleId}
                                           path only ← LPS resolves sampleId → orderId
                                           response: { orderId, patientId, testIds }

    LPS ──[BillingClient]──► Billing  POST /billing/generate/{orderId}
                                        NO BODY ← orderId is the only input
                                        Billing fetches everything else itself (see below)

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: Billing.generateInvoice(orderId)  (called by LPS above)   ║
╚══════════════════════════════════════════════════════════════════════╝
    Billing ──[OrderClient]──► Order Service  GET /orders/{orderId}/detail
                                               path only ← Billing fetches patientId+testIds
                                               response: { orderId, patientId, testIds }

    Billing ──[InventoryClient]──► Inventory  GET /tests/{id}
                                               path only ← Billing fetches price per test
                                               response: { id, code, name, price, ... }

    Billing ──[PatientClient]──► Patient  GET /patient/by-id/{patientId}
                                           path only ← Billing resolves patientId → username
                                           response: { id, username }

    Billing ──[NotificationClient]──► Notification  POST /notification
                                                      body: { username, message, type: "INVOICE_GENERATED" }

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: POST /payments  (PATIENT or ADMIN JWT)                    ║
╚══════════════════════════════════════════════════════════════════════╝
    Guards run BEFORE any DB write:
      ① Amount mismatch guard: request.amount must equal invoice.amount exactly
                               → HTTP 400 if mismatch ("₹99 ≠ ₹45, pay exact amount")
      ② Card limit guard:      CREDIT_CARD / DEBIT_CARD capped at ₹40,000 per transaction
                               → HTTP 422 if exceeded ("declined: exceeds card limit")

    If all guards pass → invoice marked PAID → payment record saved → notifications sent:

    Billing reads patientId from the Invoice record (already in billing DB)

    Billing ──[PatientClient]──► Patient  GET /patient/by-id/{patientId}
                  If PATIENT JWT → patient-service returns 403 → FeignException caught →
                  fallback: SecurityContextHolder.getAuthentication().getName() gives username
    Billing ──[NotificationClient]──► Notification  POST /notification
                                                      body: { username, message, type: "PAYMENT_SUCCESS" }

    Then immediately after — lab result delivery:
    Billing ──[OrderClient]──► Order  GET /orders/{orderId}/detail  → sampleId
    Billing ──[LpsClient]──► LPS     GET /api/jobs/results/by-sample/{sampleId}
                                       → { testId, result: {"value":5.6,"unit":"mg/dL"}, ... }
    Billing ──[NotificationClient]──► Notification  POST /notification
                                                      body: { username, message, type: "LAB_RESULT" }
                                       (skipped gracefully if LPS unavailable — payment not affected)

╔══════════════════════════════════════════════════════════════════════╗
║  TRIGGER: POST /inventory/adjust  (ADMIN or LAB_TECH JWT)           ║
║           ONLY when newQty ≤ lowStockThreshold                      ║
╚══════════════════════════════════════════════════════════════════════╝
    Inventory ──[NotificationClient]──► Notification  POST /notification
                                                        body: { username: "admin@lab.com",
                                                                message: "Low stock alert: ...",
                                                                type: "LOW_STOCK_ALERT" }
                                                        → ADMIN only, NEVER to patient
```

**JWT forwarding:** All Feign calls automatically forward the caller's JWT via `FeignClientConfig` (order-service, billing-service) or `FeignAuthInterceptor` (lps, inventory-service). Without this, downstream services would reject the call with 403.

**Fallback chain:** Every Feign client has a fallback. If a downstream service is down:
- Order → Inventory (price lookup) fails → estimated total omitted from ORDER_PLACED notification; order creation still succeeds
- Order → Patient (username lookup) fails → SecurityContext fallback used; notification still sent
- Order → Notification fails → logged; order creation/cancellation still succeeds
- Order → LPS job creation fails silently → sample collection still returns success
- LPS → Order lookup fails → billing NOT triggered (no corrupt invoices with fake IDs); error is logged
- LPS → Billing fails → logged; result approval still returns success
- Billing → Order unavailable → generateInvoice throws; LPS catches it and logs
- Billing → Inventory fails → invoice total = 0.00; logged
- Billing → Patient returns 403 (PATIENT JWT) → FeignException caught in inner try-catch → SecurityContextHolder fallback → notification still sent
- Billing → Patient truly unreachable → fallback returns null → SecurityContext checked → notification sent if caller is authenticated; skipped only if completely unresolvable
- Billing → Notification fails → logged; invoice/payment still returns success
- Inventory → Notification fails → logged; stock adjustment still returns success

---

<a name="section-4"></a>
## Section 4 — Prerequisites

### 4.1 Create MySQL databases (run once in MySQL Workbench or terminal)
```sql
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS patient_db;
CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE DATABASE IF NOT EXISTS billing;
CREATE DATABASE IF NOT EXISTS medlab;
CREATE DATABASE IF NOT EXISTS lab_processing;
CREATE DATABASE IF NOT EXISTS notification_db;
```

### 4.2 Set environment variables in IntelliJ
Go to: **Run → Edit Configurations → [select service] → Environment Variables**

These three services read DB credentials and JWT secret from environment variables:

**auth-service:**
```
DB_URL=jdbc:mysql://localhost:3306/auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
JWT_SECRET=thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab
```

**patient-service:**
```
DB_URL=jdbc:mysql://localhost:3306/patient_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
JWT_SECRET=thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab
```

**notification-service:**
```
DB_URL=jdbc:mysql://localhost:3306/notification_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
JWT_SECRET=thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab
```

> All other services (inventory, billing, order, lps, api-gateway, config-server) have credentials hardcoded in their `application.properties` / `application.yaml`. Adjust if your MySQL password differs from `root`.

### 4.3 Confirm JWT secret is identical across all services
All 7 services must use the same secret key or cross-service JWT validation will fail with 403/401.
Secret: `thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab`

---

<a name="section-5"></a>
## Section 5 — Startup Order

Start services in this exact sequence. Wait for `"Started XxxApplication in X.X seconds"` in the console before starting the next service. Services that start before Eureka is ready will fail to register.

| # | Service folder | Port | Eureka Name | Why this order |
|---|---|---|---|---|
| 1 | `server` (Eureka) | 8761 | — | Must be first; all others register here |
| 2 | `config-server` | 8888 | CONFIG-SERVER | Services pull config from here on startup |
| 3 | `auth-service` | 8081 | USER-SERVICE | Issues JWTs; gateway needs it |
| 4 | `patient_service` | 8086 | PATIENT-SERVICE | Billing calls it to resolve patientId → username |
| 5 | `inventory-service` | 8084 | INVENTORY-SERVICE | Billing calls it for test prices |
| 6 | `order-service` | 8082 | ORDER-SERVICE | LPS calls it on result approval |
| 7 | `lab-processing-service` | 8083 | LPS | Depends on Order Service |
| 8 | `Notification_service` | 8087 | NOTIFICATION-SERVICE | Billing + Inventory call it |
| 9 | `billing-service` | 8085 | BILLING-SERVICE | Depends on Inventory + Patient + Notification |
| 10 | `api-gateway` | 8090 | API-GATEWAY | Start last; routes all traffic |

> **Note:** The service *folders* are still named `patient_service` and `Notification_service` on disk — that is unchanged. Only `spring.application.name` (what Eureka sees) has changed to `patient-service` and `notification-service`.

**Verify Eureka dashboard:** Open `http://localhost:8761`

All 9 registered names must show as UP:
```
USER-SERVICE    PATIENT-SERVICE    ORDER-SERVICE    LPS
INVENTORY-SERVICE    BILLING-SERVICE    NOTIFICATION-SERVICE
CONFIG-SERVER    API-GATEWAY
```

> All HTTP calls in this guide go through the gateway: `http://localhost:8090/...`
> Use individual service URLs (e.g. `http://localhost:8084/...`) only for debugging.

---

<a name="section-6"></a>
## Section 6 — One-Time Setup (Run Once Per Fresh Database)

> **Using the automated test runner?** `test-all.bat` (or `test-all.ps1`) handles Steps A, B, C, and D automatically — you do not need to follow this section manually. The only exception is Step B (role assignment), which requires two lines of SQL in MySQL Workbench if the MySQL command-line client (`mysql.exe`) is not installed separately — the script will pause and show you exactly what to run. See Section 15 for full details.

### Step A — Register three users

```
POST http://localhost:8090/auth/register
Content-Type: application/json

{"username":"admin@lab.com","password":"Admin123"}
```
Expected: `200 OK` → `User registered successfully`

```
POST http://localhost:8090/auth/register
Content-Type: application/json

{"username":"labtech@lab.com","password":"Technician123"}
```
Expected: `200 OK` → `User registered successfully`

```
POST http://localhost:8090/auth/register
Content-Type: application/json

{"username":"patient@lab.com","password":"Patient123"}
```
Expected: `200 OK` → `User registered successfully`

---

### Step B — Assign roles in MySQL Workbench

All users register as `PATIENT` by default. Promote admin and lab tech.

Open MySQL Workbench, connect to your local instance, open a query tab, and run:
```sql
UPDATE auth_db.users SET role = 'ADMIN'    WHERE username = 'admin@lab.com';
UPDATE auth_db.users SET role = 'LAB_TECH' WHERE username = 'labtech@lab.com';
-- patient@lab.com stays as PATIENT (no update needed)
```

Verify:
```sql
SELECT username, role FROM auth_db.users;
```
Expected:
```
admin@lab.com    | ADMIN
labtech@lab.com  | LAB_TECH
patient@lab.com  | PATIENT
```

> **This is the only step that always requires Workbench (or any MySQL client).** There is no REST endpoint to change an existing user's role — it must be done directly in the database. The `test-all.ps1` script tries to do this automatically via the MySQL CLI (`mysql.exe`); if the CLI is not found, it pauses and prints these exact two lines for you to run in Workbench, then waits for you to press Enter before continuing.

> **Alternative for lab tech creation (not role promotion):** Use the admin API endpoint to create a *new* lab tech account without touching the DB:
> ```
> POST http://localhost:8090/admin/create-lab-tech
> Authorization: Bearer <ADMIN token>
> {"username":"labtech2@lab.com","password":"Tech456"}
> ```

---

### Step C — Add a lab test to the catalog

Login first to get the ADMIN token:
```
POST http://localhost:8090/auth/login
Content-Type: application/json

{"username":"admin@lab.com","password":"Admin123"}
```
Copy the `token` value from the response. Then:

```
POST http://localhost:8090/tests
Authorization: Bearer <ADMIN token>
Content-Type: application/json

{
  "code": "CBC",
  "name": "Complete Blood Count",
  "price": 45.00,
  "turnaroundHours": 24,
  "description": "Full blood panel"
}
```
Expected: `201 Created`
```json
{"id": 1, "code": "CBC", "name": "Complete Blood Count", "price": 45.00, "turnaroundHours": 24}
```
Note the test `id` = **1**. This is used when placing orders and is what Billing calls Inventory for to get `price: 45.00`.

If you see "already exists", run `GET http://localhost:8090/tests` to find the existing id.

---

### Step D — Add an inventory item (for low-stock alert testing)

The lab test catalog (Step C) and inventory items are **separate entities**:
- **Lab test catalog** (`/tests`) — what tests the lab offers and their prices
- **Inventory items** (`/inventory`) — physical lab supplies (reagents, consumables)

Add an inventory item with a low-stock threshold so the alert can be triggered.

Run in MySQL Workbench (idempotent — safe to run more than once):
```sql
INSERT INTO inventory_db.inventory_items (item_name, quantity, unit, description, low_stock_threshold)
SELECT 'CBC Reagent Kit', 20, 'units', 'Reagent kit for CBC test', 10
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_db.inventory_items WHERE item_name = 'CBC Reagent Kit'
);
```
Verify:
```sql
SELECT id, item_name, quantity, low_stock_threshold FROM inventory_db.inventory_items;
```
Expected: `1 | CBC Reagent Kit | 20 | 10`

Inventory item `id` = **1**, threshold = **10**, current stock = **20**.

> If you are using `test-all.ps1`, this INSERT is run automatically (or shown alongside the Step B SQL if the MySQL CLI is not found).

---

<a name="section-7"></a>
## Section 7 — Get Login Tokens (Repeat Every Session)

Tokens expire after **1 hour**. Re-login when you see `401 Unauthorized`.

```
POST http://localhost:8090/auth/login
{"username":"admin@lab.com","password":"Admin123"}
```
→ Copy token → save as **ADMIN token**

```
POST http://localhost:8090/auth/login
{"username":"labtech@lab.com","password":"Technician123"}
```
→ Copy token → save as **LAB_TECH token**

```
POST http://localhost:8090/auth/login
{"username":"patient@lab.com","password":"Patient123"}
```
→ Copy token → save as **PATIENT token**

Use these tokens as `Authorization: Bearer <token>` on every subsequent request.

---

<a name="section-8"></a>
## Section 8 — Complete End-to-End Flow (Happy Path)

Follow each step in order. IDs (1, 1, 1, ...) are from a clean database — adjust if yours differ.

---

### Step 1 — Create a Patient Profile

```
POST http://localhost:8090/patient/addProfile
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "age": 35,
  "gender": "MALE",
  "phoneNumber": "9876543210",
  "email": "john@example.com",
  "address": "123 Main Street"
}
```
Expected: `200 OK`
```
Profile created successfully.
```

Get the patient's DB id — needed when placing an order:
```sql
SELECT id, username FROM patient_db.patient;
```
Expected: `1 | patient@lab.com`

Patient `id` = **1**

> If you call this twice for the same user: `Profile already exists for this user.`
> That is expected — just use the existing profile.

---

### Step 2 — Place an Order

```
POST http://localhost:8090/orders/addOrder
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "tests": [1],
  "requestedBy": 1,
  "priority": "ROUTINE"
}
```
> **`patientId` is NOT sent in the request body.** Order Service fetches it automatically from Patient Service using the caller's JWT.

Expected: `201 Created`
```json
{
  "orderId": 1,
  "orderNumber": "ORD-1717000000000",
  "status": "CREATED",
  "priority": "ROUTINE"
}
```
Note `orderId` = **1** (field is `orderId`, not `id`)

**Automatic chain triggered by order placement:**
1. Order Service calls **Patient Service** `GET /patient/profile` — Patient Service reads the username from the forwarded JWT and returns the caller's own profile including their DB `id` (patientId) and `username`. No patientId in the request body — the JWT identifies the patient.
2. Order Service calls **Inventory Service** `GET /tests/{testId}` to fetch price per test → estimated total (e.g. ₹45)
3. Order Service calls **Notification Service** `POST /notification` with type `ORDER_PLACED`

**Verify ORDER_PLACED notification in MySQL:**
```sql
SELECT username, type, message FROM notification_db.notification
WHERE username = 'patient@lab.com' AND type = 'ORDER_PLACED';
```
Expected: 1 row — message contains order number and estimated total (e.g. "₹45.00")

Verify in MySQL:
```sql
SELECT id, order_number, status, priority FROM medlab.orders;
SELECT order_id, test_id FROM medlab.order_tests;
```
Expected: 1 order with status `CREATED` + 1 order_tests row linking order 1 → test 1

---

### Step 3 — Collect Sample *(auto-creates LPS processing job)*

```
POST http://localhost:8090/orders/collectSample/1?collectedBy=1
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK`
```
Sample collected successfully
```

**What Order Service does automatically:**
1. Creates a `Sample` record in DB → gets `id` = **1**
2. Updates order status → `SAMPLE_COLLECTED`
3. Loops through `order.getOrderTests()` — for each test, calls LPS `POST /api/jobs` with `{sampleId: 1, testId: 1}`
4. LPS creates a `ProcessingJob`

**Check LPS console for:**
```
[LPS] createJob called: sampleId=1, testId=1
```

**Verify in MySQL:**
```sql
-- Sample saved:
SELECT id, order_id, collected_by FROM medlab.samples;

-- Order status updated:
SELECT id, status FROM medlab.orders WHERE id = 1;

-- LPS job created:
SELECT id, sample_id, test_id, status FROM lab_processing.processing_jobs;
```
Expected:
```
samples:          1 | 1 | 1
orders:           1 | SAMPLE_COLLECTED
processing_jobs:  1 | 1 | 1 | CREATED
```

**Verify via API:**
```
GET http://localhost:8090/orders/viewOrder/1
Authorization: Bearer <PATIENT token>
```
Expected: `status: "SAMPLE_COLLECTED"`

> **If no processing_job row appears** — LPS was unreachable when collectSample ran. Create manually:
> ```
> POST http://localhost:8090/api/jobs
> Authorization: Bearer <LAB_TECH token>
> {"sampleId": 1, "testId": 1}
> ```
> The rest of the flow still works.

---

### Step 4 — Start Processing

```
POST http://localhost:8090/api/jobs/1/start
Authorization: Bearer <LAB_TECH token>
```
Expected: `200 OK`
```json
{"id": 1, "sampleId": 1, "testId": 1, "status": "IN_PROCESS", ...}
```

---

### Step 5 — Mark QC Pending

```
POST http://localhost:8090/api/jobs/1/qc
Authorization: Bearer <LAB_TECH token>
```
Expected: `200 OK`
```json
{"id": 1, "status": "QC_PENDING", ...}
```

---

### Step 6 — Enter Test Result

Use a **normal** value (≤ 10.0) so the QC flag is not triggered in the happy path:

```
POST http://localhost:8090/api/jobs/processing/1/result
Authorization: Bearer <LAB_TECH token>
Content-Type: application/json

{
  "testId": 1,
  "result": "{\"value\":5.6,\"unit\":\"mg/dL\"}",
  "enteredBy": 1
}
```
Expected: `200 OK`
```
Result entered successfully
```

Verify in MySQL:
```sql
SELECT id, result, status FROM lab_processing.results;
```
Expected: `1 | {"value":5.6,"unit":"mg/dL"} | ENTERED`

> **QC auto-flag rule:** if `value > 10.0`, a QC record with `status = FAILED` is automatically created.
> In the happy path we use 5.6 to avoid this. See Section 9 for the QC edge case test.

---

### Step 7 — Approve Result *(triggers the full Billing → Patient → Notification chain)*

```
PUT http://localhost:8090/api/jobs/processing/1/approve
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK`
```
Result approved successfully
```

**The complete automatic chain triggered by this one call:**

```
Step 7a: LPS → Order Service
    GET /orders/by-sample/1
    Response: { orderId: 1, patientId: 1, testIds: [1] }

Step 7b: LPS → Billing Service
    POST /billing/generate/1
    Body: { patientId: 1, testIds: [1] }

Step 7c: Billing → Inventory Service
    GET /tests/1
    Response: { price: 45.00, ... }
    → invoice total calculated: ₹45.00

Step 7d: Billing → Patient Service
    GET /patient/by-id/1
    Response: { id: 1, username: "patient@lab.com" }

Step 7e: Billing → Notification Service
    POST /notification
    Body: { username: "patient@lab.com",
            message: "Your test result has been approved. Invoice INV-2026-0001 of ₹45.00 has been generated. Due date: ...",
            type: "INVOICE_GENERATED" }
```

**Check console logs:**

LPS console:
```
Billing triggered for orderId=1 patientId=1
```

Billing console:
```
DEBUG  testId=1 price=45.0
INFO   Invoice generated: INV-2026-0001 | orderId=1 | amount=45.00
```

**Verify invoice in MySQL:**
```sql
SELECT id, invoice_number, order_id, patient_id, amount, status, due_date
FROM billing.invoices;
```
Expected:
```
1 | INV-2026-0001 | 1 | 1 | 45.00 | PENDING | (10 days from today)
```

| `amount` value | Meaning |
|---|---|
| `45.00` | Full chain working: LPS → Billing → Inventory ✅ |
| `0.00` | Inventory Feign fallback triggered — check Billing console for `WARN Could not fetch price` |

**Verify notification to patient in MySQL:**
```sql
SELECT username, message, type FROM notification_db.notification;
```
Expected:
```
patient@lab.com | Your test result has been approved. Invoice INV-2026-0001 of ₹45.00 ... | INVOICE_GENERATED
```

**Or via API (as the patient):**
```
GET http://localhost:8090/notification
Authorization: Bearer <PATIENT token>
```
Expected: array with 1 notification of type `INVOICE_GENERATED` ✅

---

### Step 8 — View Invoice

```
GET http://localhost:8090/invoices/1
Authorization: Bearer <PATIENT token>
```
Expected: `200 OK`
```json
{
  "id": 1,
  "invoiceNumber": "INV-2026-0001",
  "orderId": 1,
  "patientId": 1,
  "amount": 45.00,
  "currency": "INR",
  "status": "PENDING",
  "dueDate": "2026-04-23"
}
```

---

### Step 9 — Make Payment *(triggers PAYMENT_SUCCESS notification)*

```
POST http://localhost:8090/payments
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "invoiceId": 1,
  "paymentMethod": "UPI",
  "amount": 45.00
}
```
Valid `paymentMethod` values: `UPI`, `CREDIT_CARD`, `DEBIT_CARD`

> **Payment Validation Guards (run before any DB write):**
> 1. **Amount mismatch** — `amount` must exactly match the invoice amount. Sending ₹99 for a ₹45 invoice → `400 Bad Request`  
>    `"Payment amount ₹99 does not match invoice amount ₹45. Please pay the exact invoice amount."`
> 2. **Card transaction limit** — `CREDIT_CARD` and `DEBIT_CARD` are capped at **₹40,000** per transaction. Sending ₹41,000 via card → `422 Unprocessable Entity`  
>    `"CREDIT_CARD transaction declined: amount ₹41000 exceeds card limit of ₹40000. Please use UPI."`
>
> UPI has no transaction limit. Only the exact-amount guard applies to UPI.

Expected: `201 Created`
```json
{
  "transactionId": "TXN-20260413-001",
  "invoiceId": 1,
  "amount": 45.00,
  "paymentMethod": "UPI",
  "paymentStatus": "PAID",
  "paidAt": "2026-04-13T10:30:00",
  "message": "Payment successful. Transaction ID: TXN-20260413-001"
}
```

**Payment gateway is simulated — no real gateway, always returns PAID when all guards pass.**

**Automatic chain:**
1. Guards validated (amount match + card limit)
2. Billing marks invoice → `PAID`
3. Billing → Patient Service: resolves patientId 1 → username `patient@lab.com`
4. Billing → Notification Service: sends `PAYMENT_SUCCESS` notification
5. Billing → Order Service: `GET /orders/{orderId}/detail` → gets `sampleId`
6. Billing → LPS: `GET /api/jobs/results/by-sample/{sampleId}` → gets approved test result
7. Billing → Notification Service: sends `LAB_RESULT` notification with the actual result value

**Verify invoice is now PAID:**
```sql
SELECT invoice_number, amount, status FROM billing.invoices WHERE id = 1;
```
Expected: `INV-2026-0001 | 45.00 | PAID`

**Verify payment record:**
```sql
SELECT invoice_id, amount_paid, payment_method, transaction_id, status, paid_at
FROM billing.payments;
```
Expected: `1 | 45.00 | UPI | TXN-20260413-001 | PAID | (timestamp)`

**Verify all three notifications for the patient:**
```sql
SELECT username, type, message FROM notification_db.notification ORDER BY id;
```
Expected:
```
patient@lab.com | INVOICE_GENERATED  | Your test result has been approved. Invoice INV-2026-0001...
patient@lab.com | PAYMENT_SUCCESS    | Payment of ₹45.00 via UPI was successful. Transaction ID: TXN-...
patient@lab.com | LAB_RESULT         | Your lab test result is ready! Test #1 result: {"value":5.6,"unit":"mg/dL"}...
```

**Via API:**
```
GET http://localhost:8090/notification
Authorization: Bearer <PATIENT token>
```
Expected: 3 notifications (INVOICE_GENERATED + PAYMENT_SUCCESS + LAB_RESULT) ✅

---

### Step 10 — Try Paying Again (State Machine Guard)

```
POST http://localhost:8090/payments
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "invoiceId": 1,
  "paymentMethod": "CREDIT_CARD",
  "amount": 45.00
}
```
Expected: `422 Unprocessable Entity`
```
Invoice INV-2026-0001 is already PAID.
```
Double-payment protection working ✅

---

<a name="section-9"></a>
## Section 9 — Individual Feature & Edge Case Tests

### 9.1 — QC Auto-Flag (Abnormal Result)

For this test, use a **new** order + sample + job (or reset the existing job to CREATED status).
Create a new order and collect sample (repeat Steps 2–3), so you get `jobId = 2`, then enter a result with `value > 10`:

```
POST http://localhost:8090/api/jobs/processing/2/result
Authorization: Bearer <LAB_TECH token>
Content-Type: application/json

{
  "testId": 1,
  "result": "{\"value\":150.0,\"unit\":\"mg/dL\"}",
  "enteredBy": 1
}
```
Expected: `200 OK` → `Result entered successfully`

**LPS console prints:**
```
EVENT: QCAlert → Job 2 flagged (value=150.0)
```

**Verify in MySQL:**
```sql
SELECT job_id, remarks, qc_status FROM lab_processing.qc_records;
```
Expected: `2 | Abnormal result value detected: 150.0 | FAILED`

The QC flag does **not** block approval — you can still call `approve` on the same job if needed.

---

### 9.2 — Low-Stock Alert (Inventory → Admin Notification, NOT Patient)

Reduce the inventory item below its threshold (threshold = 10, current stock = 20 from setup):

```
POST http://localhost:8090/inventory/adjust
Authorization: Bearer <ADMIN token>
Content-Type: application/json

{
  "itemId": 1,
  "quantityChange": -15,
  "reason": "Used for test batch"
}
```
Expected: `200 OK`
```json
{
  "id": 1,
  "itemName": "CBC Reagent Kit",
  "quantity": 5,
  "unit": "units",
  "lowStockThreshold": 10,
  "lowStock": true
}
```

**What Inventory Service does automatically** (because `5 <= 10`):
```
Inventory → Notification Service  POST /notification
Body: {
  "username": "admin@lab.com",
  "message": "Low stock alert: 'CBC Reagent Kit' has only 5 units remaining (threshold: 10). Please reorder.",
  "type": "LOW_STOCK_ALERT"
}
```

**Inventory console:**
```
WARN  LOW_STOCK_ALERT sent to admin 'admin@lab.com': item='CBC Reagent Kit' qty=5 threshold=10
```

**Verify admin received the notification:**
```sql
SELECT username, message, type FROM notification_db.notification
WHERE type = 'LOW_STOCK_ALERT';
```
Expected: `admin@lab.com | Low stock alert: 'CBC Reagent Kit' has only 5 units... | LOW_STOCK_ALERT`

**Verify the patient received NO low-stock notification:**
```
GET http://localhost:8090/notification
Authorization: Bearer <PATIENT token>
```
Expected: only `INVOICE_GENERATED` / `PAYMENT_SUCCESS` notifications — NO `LOW_STOCK_ALERT` ✅

**No alert when stock is above threshold:**
```
POST http://localhost:8090/inventory/adjust
Authorization: Bearer <ADMIN token>
{"itemId": 1, "quantityChange": 20, "reason": "Restock"}
```
Expected: `200 OK` with `quantity: 25, lowStock: false` — no notification sent, nothing in console ✅

> **To change the admin who receives alerts:**
> Edit `inventory-service/src/main/resources/application.yaml`:
> ```yaml
> inventory:
>   notification:
>     admin-username: youradmin@lab.com
> ```
> Restart inventory-service for the change to take effect.

---

### 9.3 — RBAC: Role Enforcement on Inventory

**PATIENT cannot create tests (should get 403):**
```
POST http://localhost:8090/tests
Authorization: Bearer <PATIENT token>
{"code":"X","name":"X","price":10,"turnaroundHours":1,"description":"X"}
```
Expected: `403 Forbidden` ✅

**ADMIN can create tests:**
```
POST http://localhost:8090/tests
Authorization: Bearer <ADMIN token>
{"code":"LFT","name":"Liver Function Test","price":75.00,"turnaroundHours":48,"description":"Liver panel"}
```
Expected: `201 Created` ✅

**Everyone can view tests:**
```
GET http://localhost:8090/tests
Authorization: Bearer <PATIENT token>
```
Expected: `200 OK` — array of tests ✅

---

### 9.4 — RBAC: Auth-Service Endpoints

**Only ADMIN can see all users:**
```
GET http://localhost:8090/admin/users
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK` — list of all registered users ✅

```
GET http://localhost:8090/admin/users
Authorization: Bearer <PATIENT token>
```
Expected: `403 Forbidden` ✅

**Only ADMIN can create lab techs via API:**
```
POST http://localhost:8090/admin/create-lab-tech
Authorization: Bearer <ADMIN token>
{"username":"labtech2@lab.com","password":"Tech456"}
```
Expected: `200 OK` → `Lab Technician Created` ✅

```
POST http://localhost:8090/admin/create-lab-tech
Authorization: Bearer <LAB_TECH token>
{"username":"someone@lab.com","password":"pass"}
```
Expected: `403 Forbidden` ✅

**Only LAB_TECH can upload reports:**
```
POST http://localhost:8090/labTech/upload
Authorization: Bearer <LAB_TECH token>
```
Expected: `200 OK` → `Report Uploaded` ✅

```
POST http://localhost:8090/labTech/upload
Authorization: Bearer <PATIENT token>
```
Expected: `403 Forbidden` ✅

---

### 9.5 — Direct Billing (Isolated Billing Test, Bypassing LPS)

Useful for testing billing in isolation without going through the full flow:
```
POST http://localhost:8090/billing/generate/9001
Authorization: Bearer <ADMIN token>
Content-Type: application/json

{
  "patientId": 1,
  "testIds": [1]
}
```
Expected: `201 Created`
```json
{
  "id": 2,
  "invoiceNumber": "INV-2026-0002",
  "orderId": 9001,
  "patientId": 1,
  "amount": 45.00,
  "status": "PENDING"
}
```
`amount = 45.00` confirms Billing → Inventory price lookup is working ✅

---

### 9.6 — Internal Endpoint Verification

**Order Service by-sample lookup (used internally by LPS):**
```
GET http://localhost:8090/orders/by-sample/1
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK`
```json
{"orderId": 1, "patientId": 1, "testIds": [1]}
```

**Patient lookup by id (used internally by Billing):**
```
GET http://localhost:8090/patient/by-id/1
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK` — full patient object including `username: "patient@lab.com"` ✅

**PATIENT cannot access either internal endpoint:**
```
GET http://localhost:8090/orders/by-sample/1
Authorization: Bearer <PATIENT token>
```
Expected: `403 Forbidden` ✅

---

### 9.7 — Notification Broadcast (Admin Only)

```
POST http://localhost:8090/notification/broadcast
Authorization: Bearer <ADMIN token>
Content-Type: application/json

{"message": "System maintenance tonight at 10pm"}
```
Expected: `200 OK` → `Broadcast notification sent successfully!` ✅

```
POST http://localhost:8090/notification/broadcast
Authorization: Bearer <PATIENT token>
```
Expected: `403 Forbidden` ✅

---

### 9.8 — Invoice Already Exists Guard (Idempotency)

Trying to generate a second invoice for the same orderId (no request body — Billing pulls everything from Order Service):
```
POST http://localhost:8090/billing/generate/1
Authorization: Bearer <ADMIN token>
```
Expected: `409 Conflict`
```
Invoice already exists for orderId=1 → INV-2026-0001
```
One invoice per order enforced ✅

---

### 9.9 — Unauthenticated Request (No Token)

```
GET http://localhost:8090/orders/viewOrder/1
```
Expected: `401 Unauthorized` — no JWT provided ✅

---

### 9.10 — ORDER_CANCELLED Notification

Create a spare order and immediately cancel it to verify the notification chain:

```
# Create spare order
POST http://localhost:8090/orders/addOrder
Authorization: Bearer <PATIENT token>
Content-Type: application/json
{"patientId": 1, "tests": [1], "requestedBy": 1, "priority": "ROUTINE"}
# → note orderId from response

# Cancel it
POST http://localhost:8090/orders/cancelOrder/{orderId}
Authorization: Bearer <PATIENT token>
```
Expected: `200 OK` — `"Order cancelled successfully"`

**Verify ORDER_CANCELLED notification:**
```sql
SELECT username, type, message FROM notification_db.notification
WHERE username = 'patient@lab.com' AND type = 'ORDER_CANCELLED';
```
Expected: 1+ rows — message contains the order number and support contact note ✅

---

### 9.11 — Payment Validation Guards

#### 9.11a — Wrong Amount (400)

Using the invoice generated in 9.5 (orderId2, amount = ₹45):
```
POST http://localhost:8090/payments
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "invoiceId": <invId2>,
  "paymentMethod": "UPI",
  "amount": 99.00
}
```
Expected: `400 Bad Request`
```
Payment amount ₹99 does not match invoice amount ₹45.00. Please pay the exact invoice amount.
```

#### 9.11b — Card Transaction Limit (422)

To test the card limit guard in isolation, create an expensive test (price = ₹50,000) and generate an invoice for it, then attempt a card payment:

```sql
-- Create expensive test directly in DB (or via API with ADMIN token)
INSERT INTO inventory_db.tests (code, name, price, turnaround_hours, description)
VALUES ('EXP', 'Expensive Test', 50000.00, 24, 'For card limit testing');
```

```
# Place order with the expensive test
POST http://localhost:8090/orders/addOrder
Authorization: Bearer <PATIENT token>
{"patientId": 1, "tests": [<expTestId>], "requestedBy": 1, "priority": "ROUTINE"}

# ... collect sample, start, QC, enter result, approve → invoice generated

# Attempt CREDIT_CARD payment (₹50,000 > ₹40,000 limit)
POST http://localhost:8090/payments
Authorization: Bearer <PATIENT token>
{
  "invoiceId": <expInvoiceId>,
  "paymentMethod": "CREDIT_CARD",
  "amount": 50000.00
}
```
Expected: `422 Unprocessable Entity`
```
CREDIT_CARD transaction declined: amount ₹50000 exceeds card transaction limit of ₹40000. Please use UPI or split the payment.
```

Same guard applies to `DEBIT_CARD`. `UPI` has no limit — the ₹50,000 UPI payment would succeed.

#### 9.11c — Correct Amount + UPI (201 PAID)

```
POST http://localhost:8090/payments
Authorization: Bearer <PATIENT token>
Content-Type: application/json

{
  "invoiceId": <invId2>,
  "paymentMethod": "UPI",
  "amount": 45.00
}
```
Expected: `201 Created` — `"paymentStatus": "PAID"` ✅

#### 9.11d — LAB_RESULT notification delivered after payment

After the payment above succeeds, Billing automatically:
1. Calls Order Service `GET /orders/{orderId}/detail` → gets `sampleId`
2. Calls LPS `GET /api/jobs/results/by-sample/{sampleId}` → gets the approved result
3. Sends a `LAB_RESULT` notification to the patient

Verify the notification was created:
```sql
SELECT username, type, message
FROM notification_db.notification
WHERE type = 'LAB_RESULT';
```
Expected:
```
patient@lab.com | LAB_RESULT | Your lab test result is ready! Test #1 result: {"value":5.6,"unit":"mg/dL"}  Please consult your doctor for interpretation.
```

Via API (patient reads their own notifications):
```
GET http://localhost:8090/notification
Authorization: Bearer <PATIENT token>
```
Expected response includes a notification with `type: "LAB_RESULT"` containing the actual result JSON. ✅

> **Note:** The LAB_RESULT notification is delivered **after** PAYMENT_SUCCESS, within the same payment request.
> If LPS is down, the payment still succeeds — the result notification is simply skipped (fallback logs a warning and returns null).

---

<a name="section-10"></a>
## Section 10 — Fallback / Resilience Tests (What Happens When a Service is Down)

These tests verify that the system degrades gracefully rather than crashing.

### 10.1 — LPS Down During collectSample

Stop the LPS service. Then run:
```
POST http://localhost:8090/orders/collectSample/2?collectedBy=1
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK` → `Sample collected successfully`

**What happens:**
- Sample is saved ✅
- Order status → `SAMPLE_COLLECTED` ✅
- LPS Feign call fails → fallback logs: `Failed to create LPS job for sampleId=2 testId=1: ...`
- No processing_job row for sampleId=2 in lab_processing DB — must be created manually later

Restart LPS. Manually create the job:
```
POST http://localhost:8090/api/jobs
Authorization: Bearer <LAB_TECH token>
{"sampleId": 2, "testId": 1}
```

---

### 10.2 — Order Service Down During approveResult

Stop order-service. Then approve a result:
```
PUT http://localhost:8090/api/jobs/processing/1/approve
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK` → `Result approved successfully`

**What happens:**
- Result status → `APPROVED`, job → `COMPLETED` ✅
- OrderClient fallback returns `null` → LPS uses `sampleId` as proxy for `orderId` and `patientId`
- LPS console: `OrderService unavailable — using sampleId as orderId/patientId proxy: ...`
- Billing is called with `orderId = sampleId` and `patientId = sampleId` (both = 1)
- Invoice is created but with `patientId = 1` (sampleId, which happens to be the same in a clean DB)
- **Data quality is degraded** but the system does not crash ✅

---

### 10.3 — Inventory Service Down During Invoice Generation

Stop inventory-service. Then trigger approval (or direct billing call):
```
POST http://localhost:8090/billing/generate/9002
Authorization: Bearer <ADMIN token>
{"patientId": 1, "testIds": [1]}
```
Expected: `201 Created`

**What happens:**
- Billing console: `WARN Could not fetch price for testId=1 (InventoryService unavailable): ...`
- Invoice is created with `amount = 0.00` instead of 45.00
- Invoice itself is NOT blocked ✅ — but amount is wrong
- Look for `amount = 0.00` in the response as the signal that Inventory was down

---

### 10.4 — Notification Service Down During Invoice/Payment

Stop notification-service. Then approve a result (or pay an invoice):
```
PUT http://localhost:8090/api/jobs/processing/1/approve
Authorization: Bearer <ADMIN token>
```
Expected: `200 OK` → invoice is created successfully

**What happens:**
- Billing generates invoice ✅
- PatientClient call succeeds → gets username
- NotificationClient call fails → fallback: `[FALLBACK] Notification_service unreachable — notification not sent`
- Invoice amount is correct ✅, patient just doesn't receive the notification

---

### 10.5 — Notification Service Down During Low-Stock Alert

Stop notification-service. Then reduce stock below threshold:
```
POST http://localhost:8090/inventory/adjust
Authorization: Bearer <ADMIN token>
{"itemId": 1, "quantityChange": -5, "reason": "Test"}
```
Expected: `200 OK` with the updated stock — `lowStock: true`

**What happens:**
- Stock adjustment saves to DB ✅
- Notification call fails → fallback logs:
  ```
  [FALLBACK] Notification_service unreachable — low-stock alert NOT sent. Details: username=admin@lab.com | message=Low stock alert...
  ```
- Admin does NOT get the notification, but the stock update is not lost ✅

---

<a name="section-11"></a>
## Section 11 — RBAC Quick Reference

| Endpoint | PATIENT | LAB_TECH | ADMIN | Notes |
|---|---|---|---|---|
| `POST /auth/register` | ✅ open | ✅ open | ✅ open | No token needed |
| `POST /auth/login` | ✅ open | ✅ open | ✅ open | No token needed |
| `POST /admin/create-lab-tech` | ❌ 403 | ❌ 403 | ✅ | |
| `GET /admin/users` | ❌ 403 | ❌ 403 | ✅ | |
| `POST /labTech/upload` | ❌ 403 | ✅ | ❌ 403 | |
| `GET /labTech/reports` | ❌ 403 | ✅ | ❌ 403 | |
| `GET /patient/profile` | ✅ | ❌ 403 | ❌ 403 | Own profile only |
| `POST /patient/addProfile` | ✅ | ❌ 403 | ❌ 403 | |
| `PUT /patient/updateProfile` | ✅ | ❌ 403 | ❌ 403 | |
| `GET /patient/by-id/{id}` | ❌ 403 | ✅ | ✅ | Internal; used by Billing |
| `GET /tests` | ✅ | ✅ | ✅ | |
| `GET /tests/{id}` | ✅ | ✅ | ✅ | |
| `POST /tests` | ❌ 403 | ❌ 403 | ✅ | |
| `PUT /tests/{id}` | ❌ 403 | ❌ 403 | ✅ | |
| `GET /inventory` | ✅ | ✅ | ✅ | |
| `POST /inventory/adjust` | ❌ 403 | ✅ | ✅ | Sends LOW_STOCK_ALERT to admin if stock ≤ threshold |
| `POST /orders/addOrder` | ✅ | ✅ | ❌ 403 | |
| `GET /orders/viewOrder/{id}` | ✅ | ✅ | ❌ 403 | |
| `POST /orders/collectSample/{id}` | ❌ 403 | ❌ 403 | ✅ | Auto-creates LPS jobs |
| `POST /orders/cancelOrder/{id}` | ✅ | ✅ | ❌ 403 | |
| `GET /orders/by-sample/{id}` | ❌ 403 | ✅ | ✅ | Internal; used by LPS |
| `POST /api/jobs` | ❌ 403 | ✅ | ✅ | ADMIN needed: Order Service calls with ADMIN JWT |
| `POST /api/jobs/{id}/start` | ❌ 403 | ✅ | ❌ 403 | |
| `POST /api/jobs/{id}/qc` | ❌ 403 | ✅ | ❌ 403 | |
| `POST /api/jobs/{id}/complete` | ❌ 403 | ✅ | ❌ 403 | |
| `POST /api/jobs/{id}/cancel` | ❌ 403 | ❌ 403 | ✅ | |
| `POST /api/jobs/processing/{id}/result` | ❌ 403 | ✅ | ❌ 403 | Auto-flags QC if value > 10 |
| `PUT /api/jobs/processing/{id}/approve` | ❌ 403 | ❌ 403 | ✅ | Triggers full billing chain |
| `POST /billing/generate/{orderId}` | ❌ 403 | ✅ | ✅ | |
| `GET /invoices/{id}` | ✅ | ✅ | ✅ | |
| `GET /invoices/order/{orderId}` | ✅ | ✅ | ✅ | |
| `GET /invoices/patient/{patientId}` | ✅ | ❌ 403 | ✅ | |
| `POST /payments` | ✅ | ❌ 403 | ✅ | Always mocked — always PAID |
| `GET /payments/{invoiceId}` | ✅ | ❌ 403 | ✅ | |
| `POST /notification` | ✅ open | ✅ open | ✅ open | No token needed (internal use) |
| `GET /notification` | ✅ | ❌ 403 | ❌ 403 | Patient sees own notifications only |
| `POST /notification/broadcast` | ❌ 403 | ❌ 403 | ✅ | |

---

<a name="section-12"></a>
## Section 12 — Port & Service Reference

| Service | Port | Eureka Name | Database | Feign calls TO |
|---|---|---|---|---|
| discovery-server (Eureka) | 8761 | — | — | — |
| config-server | 8888 | CONFIG-SERVER | — | — |
| auth-service | 8081 | USER-SERVICE | auth_db | — |
| order-service | 8082 | ORDER-SERVICE | medlab | LPS |
| lps | 8083 | LPS | lab_processing | Order Service, Billing |
| inventory-service | 8084 | INVENTORY-SERVICE | inventory_db | notification-service |
| billing-service | 8085 | BILLING-SERVICE | billing | Inventory, patient-service, notification-service |
| patient-service | 8086 | PATIENT-SERVICE | patient_db | — |
| notification-service | 8087 | NOTIFICATION-SERVICE | notification_db | — |
| api-gateway | 8090 | API-GATEWAY | — | All services |

> **Folder names vs Eureka names:** The folders on disk are still `patient_service` and `Notification_service`. Only `spring.application.name` inside each service's `application.properties` was changed to `patient-service` / `notification-service`. Do not rename the folders.

### Gateway route → service mapping
| Path prefix | Routed to | Notes |
|---|---|---|
| `/auth/**`, `/admin/**`, `/labTech/**` | USER-SERVICE (auth-service) | |
| `/patient/profile`, `/patient/reports` | USER-SERVICE (auth-service) | auth-service has its own PatientController for these two paths |
| `/patient/**` (all other paths) | PATIENT-SERVICE (patient-service) | `addProfile`, `updateProfile`, `by-id/{id}` |
| `/orders/**` | ORDER-SERVICE | |
| `/api/jobs/**` | LPS | |
| `/tests/**`, `/inventory/**` | INVENTORY-SERVICE | |
| `/billing/**`, `/invoices/**`, `/payments/**` | BILLING-SERVICE | |
| `/notification/**` | NOTIFICATION-SERVICE | |

### Swagger UIs (for testing individual services directly, without gateway)
| Service | Swagger URL |
|---|---|
| All services (aggregated via gateway) | `http://localhost:8090/swagger-ui.html` |
| auth-service | `http://localhost:8081/swagger-ui/index.html` |
| patient-service | `http://localhost:8086/swagger-ui/index.html` |
| inventory-service | `http://localhost:8084/swagger-ui/index.html` |
| order-service | `http://localhost:8082/swagger-ui/index.html` |
| lps | `http://localhost:8083/swagger-ui/index.html` |
| notification-service | `http://localhost:8087/swagger-ui/index.html` |
| billing-service | `http://localhost:8085/swagger-ui/index.html` |

---

<a name="section-13"></a>
## Section 13 — JWT Rules

| Item | Value |
|---|---|
| Secret (all services) | `thisisverysecuresecretkeyforjwttokengenerationandvalidationteam5medlab` |
| Role stored in JWT claim | Plain string: `ADMIN`, `LAB_TECH`, or `PATIENT` |
| How stored in SecurityContext | `new SimpleGrantedAuthority("ADMIN")` — no `ROLE_` prefix |
| How checked in `@PreAuthorize` | `hasAuthority('ADMIN')` — never `hasRole()` |
| Why `hasRole()` always fails | `hasRole('X')` looks for `"ROLE_X"` — which is never in the context |
| Token expiry | 1 hour — re-login to get a fresh token |
| Open endpoints (no token needed) | `/auth/register`, `/auth/login`, `POST /notification`, all Swagger paths |
| Services that forward JWT in Feign calls | order-service, lps, inventory-service, billing-service |
| How JWT forwarding works | `FeignAuthInterceptor` / `FeignClientConfig` reads `Authorization` header from the current HTTP request and copies it to all outbound Feign calls |

---

<a name="section-14"></a>
## Section 14 — Troubleshooting Common Errors

| Symptom | Likely Cause | Fix |
|---|---|---|
| `401 Unauthorized` | No token or expired token | Re-login and use the new token |
| `403 Forbidden` on a role endpoint | Wrong role for that endpoint, or `hasRole()` used instead of `hasAuthority()`, or `@EnableMethodSecurity` missing | Check RBAC table (Section 11); verify SecurityConfig has `@EnableMethodSecurity` |
| `403 Forbidden` on an inter-service Feign call | JWT not forwarded, or downstream service requires a role the caller doesn't have | Check FeignAuthInterceptor is present and `@Bean` on the interceptor method; check `createJob` allows `ADMIN` |
| `invoice amount = 0.00` | Inventory Service was down when Billing tried to fetch price | Start inventory-service; Billing will fetch correctly on next invoice |
| No `processing_job` row after collectSample | LPS was down or not registered in Eureka | Start LPS and create the job manually: `POST /api/jobs` |
| No notification row after approve/payment | notification-service down, OR PatientClient returned null (patient profile not created) | Check notification-service is running; ensure `POST /patient/addProfile` was done first |
| `Profile already exists` on addProfile | Already called addProfile for this user | Not an error — just use the existing profile |
| `Invoice already exists for orderId=X` | Trying to generate a second invoice for the same order | Expected idempotency guard — one invoice per order |
| `Insufficient stock. Current quantity: X` | Trying to reduce stock below 0 | Use a smaller `quantityChange` magnitude |
| Service not appearing in Eureka at `http://localhost:8761` | Service started before Eureka was ready, or has a startup error | Restart the service after Eureka is fully up |
| `Could not fetch price for testId=X` in Billing logs | Inventory Service not reachable | Ensure inventory-service is running and registered in Eureka |
| `[FALLBACK] Notification_service unreachable` in logs | notification-service not running | Start notification-service; existing stock adjustments and billing calls did succeed (just no notification) |
| `IllegalStateException: Service id not legal hostname (Xxx_service)` at startup | A `@FeignClient(name=...)` value contains an underscore — OpenFeign 5.x enforces RFC hostname rules | Change the offending Feign client name to use a hyphen (e.g. `notification-service`, `patient-service`) AND update `spring.application.name` in that service to match |
| `404 Not Found` from gateway on `/patient/profile` or `/patient/reports` | Before the routing fix, these two paths were mistakenly forwarded to patient-service instead of auth-service | Ensure you have the latest `api-gateway/application.yml` — these paths are now explicitly listed in the `user-service` route above the `patient-service` route |
| `404` from gateway for valid paths like `/auth/register` | `@EnableDiscoveryClient` missing from `ApiGatewayApplication` — reactive Eureka client not activated, `lb://` URIs can't resolve, `ReactiveLoadBalancerClientFilter` throws `NotFoundException` → 404 | Ensure `@EnableDiscoveryClient` is on `ApiGatewayApplication`; verify Eureka shows all services UP at `http://localhost:8761` |
| `404` from gateway even though route exists | Discovery locator (`discovery.locator.enabled: true`) auto-generated routes interfering with explicit routes in Spring Cloud Gateway 4.x | Set `spring.cloud.gateway.discovery.locator.enabled: false` in gateway `application.yml` |
| Can't tell if 404 is "no route" or "service down" | `loadbalancer.use404` defaulting to ambiguous value | Set `spring.cloud.gateway.loadbalancer.use404: false` — service-not-found then returns 503, route-not-matched returns 404; they're now distinguishable |
| Want to verify all gateway routes are loaded | — | Hit `GET http://localhost:8090/actuator/gateway/routes` — returns JSON array of every active route with its predicates and target URI |
| `503 Service Unavailable` from gateway | Service is running but not yet registered in Eureka, OR `lb://` name doesn't match `spring.application.name` | Wait 30 sec after startup for Eureka registration; verify name matches (e.g. `lb://user-service` for `spring.application.name=user-service`) |
| Token works on gateway but 403 on direct service call | JWT secret mismatch between services | Verify all services use the same `JWT_SECRET` / `jwt.secret` value |

---

<a name="section-15"></a>
## Section 15 — Automated Test Runner

Three scripts are provided in the project root for automated testing. All use **individual service ports (8081-8087)**, not the API gateway (8090), to isolate any gateway issues from the services under test.

---

### Files

| File | Purpose |
|---|---|
| `test-all.ps1` | PowerShell test runner — covers guide.md Sections 6–9. Registers users, assigns roles via MySQL, captures JWT tokens, runs every endpoint test, prints PASS/FAIL/SKIP with colors, and shows a final summary. |
| `test-all.bat` | One-click launcher for `test-all.ps1`. Double-click in Explorer or run from CMD. Services must already be running. |
| `debug.bat` | Combined launcher: starts all services (via `start-all.ps1`), then waits up to 120 seconds for them to be ready (press ENTER at any point to skip the wait immediately), then runs `test-all.ps1` automatically. |

---

### Running the PowerShell test runner

**Option A — Services already running (most common):**
```
test-all.bat
```
or directly:
```powershell
.\test-all.ps1
.\test-all.ps1 -DbPassword "yourpassword"   # if MySQL password is not 'root'
```

**Option B — Start everything and test in one shot:**
```
debug.bat
```
This starts all services, then shows a live countdown. Press **ENTER** at any time to skip straight to the tests, or it will auto-continue after 120 seconds. The countdown exists because Spring Boot services take 30-60 seconds each to fully start and register with Eureka — if tests run before services are ready, they will all show as DOWN in Section 0 and be skipped.

**Parameters for `test-all.ps1`:**
| Parameter | Default | Description |
|---|---|---|
| `-DbUser` | `root` | MySQL username |
| `-DbPassword` | `root` | MySQL password |
| `-DbHost` | `localhost` | MySQL host |
| `-DbPort` | `3306` | MySQL port |

---

### What the PowerShell runner does (step by step)

1. **Section 0 — Port availability check:** TCP-connects to ports 8081-8087. Skips all tests for a service that is not reachable.
2. **Section 6A — Register users:** `POST /auth/register` for admin@lab.com, labtech@lab.com, patient@lab.com. Already-registered users are skipped gracefully.
3. **Section 6B — Role assignment:** Runs `UPDATE auth_db.users SET role=...` via the MySQL CLI. Finds `mysql.exe` automatically (searches common install paths). **If MySQL CLI is not found** (e.g. you have MySQL Workbench but not the Server CLI in your PATH), the script pauses, prints the exact SQL to run, and waits for you to press Enter after running it in Workbench. It then marks those steps as SKIP (not FAIL).
4. **Section 6C — Add CBC test:** `POST /tests` with the CBC test definition (price 45.00).
5. **Section 6D — Add inventory item:** Inserts a `CBC Reagent Kit` row via MySQL CLI (idempotent — skips if it already exists). If CLI is not found, the SQL was already shown in step 6B — run it in Workbench at the same time.
6. **Section 7 — Login all three users:** Captures `token` from each response into `$adminToken`, `$labTechToken`, `$patientToken`.
7. **Section 8 — E2E flow:** Runs Steps 1-10 (patient profile → order → collect sample → start → QC → result → approve → invoice → payment → double-payment guard). DB is verified with MySQL queries at key steps (invoice amount, payment record, notification count).
8. **Section 9 — Edge cases:** QC auto-flag (abnormal value > 10), low-stock alert (stock reduced below threshold), RBAC enforcement (PATIENT/LAB_TECH/ADMIN combinations), direct billing, internal endpoint access control, notification broadcast, invoice idempotency, unauthenticated request, ORDER_CANCELLED notification (9.10), payment validation guards — wrong amount (9.11a), card limit check (9.11b), correct UPI payment (9.11c), LAB_RESULT notification after payment (9.11d).

---

### Sample output

```
+============================================================+
|   MedLab API Test Runner  --  guide.md Sections 6-9       |
|   Individual ports (8081-8087), NOT gateway (8090)         |
+============================================================+

  ---- Section 0 -- Service Availability ----
    [UP]   auth-service     :8081
    [UP]   order-service    :8082
    [UP]   lps              :8083
    [UP]   inventory-service:8084
    [UP]   billing-service  :8085
    [UP]   patient-service  :8086
    [UP]   notification-svc :8087

  ---- Section 5.5 -- Cleanup (resetting test data for a clean run) ----
  All test tables cleared. Starting with a clean slate.

  ---- Section 6 -- One-Time Setup ----
    [PASS] 6A-1 Register admin@lab.com  (HTTP 200)
    [PASS] 6B-1 MySQL: SET role=ADMIN for admin@lab.com
    [PASS] 6C Add CBC test (testId=1)  (HTTP 201)
    ...

  ---- Section 8 -- End-to-End Flow (Happy Path) ----
    [PASS] 8.7b Invoice generated (amount=45.00)
    [PASS] 8.7c INVOICE_GENERATED notification sent to patient
    [PASS] 8.9 Payment accepted  (status=PAID txnId=TXN-...)
    [PASS] 8.9b PAYMENT_SUCCESS notification
    ...

+============================================================+
|   TEST SUMMARY                                             |
+============================================================+

  [PASS] 6A-1 Register admin@lab.com  -- HTTP 200
  [PASS] 7-1 Login admin@lab.com  -- token captured
  [PASS] 8.2 Place order  -- orderId=1 HTTP 201
  [PASS] 8.7b Invoice generated (amount=45.00)
  [PASS] 8.9b PAYMENT_SUCCESS notification
  [PASS] 9.1b QC record FAILED auto-created
  ...

  Total: 44   PASS: 44   FAIL: 0   SKIP: 0

  All tests passed!
```

A **FAIL on "Invoice amount check" (0.00 instead of 45.00)** means the Billing → Inventory Feign call is failing (Inventory service down or not registered).

A **FAIL on "PAYMENT_SUCCESS notification"** means billing-service was not restarted after the `PaymentService.java` edit — restart it and re-run.

