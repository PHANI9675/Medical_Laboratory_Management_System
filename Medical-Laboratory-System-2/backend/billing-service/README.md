# MedLab Billing Service

**Team 5 — P5 | The Cashier**
Port: `8085` | Database: `billing` | Spring Boot 4.0.5 · Java 21 · MySQL 8

---

## Folder Structure

```
backend/billing-service/
├── src/
│   ├── main/
│   │   ├── java/com/medlab/billing/
│   │   │   ├── controller/
│   │   │   │   ├── BillingController.java
│   │   │   │   └── PaymentController.java
│   │   │   ├── dto/
│   │   │   │   ├── GenerateInvoiceRequest.java
│   │   │   │   ├── InvoiceResponse.java
│   │   │   │   ├── NotificationRequest.java
│   │   │   │   ├── PaymentRequest.java
│   │   │   │   └── PaymentResponse.java
│   │   │   ├── exception/
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── InvoiceAlreadyExistsException.java
│   │   │   │   ├── InvalidPaymentStateException.java
│   │   │   │   └── ResourceNotFoundException.java
│   │   │   ├── model/
│   │   │   │   ├── Claim.java
│   │   │   │   ├── Invoice.java
│   │   │   │   ├── InvoiceStatus.java
│   │   │   │   ├── Payment.java
│   │   │   │   ├── PaymentMethod.java
│   │   │   │   └── PaymentStatus.java
│   │   │   ├── repository/
│   │   │   │   ├── ClaimRepository.java
│   │   │   │   ├── InvoiceRepository.java
│   │   │   │   └── PaymentRepository.java
│   │   │   ├── service/
│   │   │   │   ├── BillingService.java
│   │   │   │   ├── InvoiceNumberGenerator.java
│   │   │   │   ├── PaymentService.java
│   │   │   │   └── TransactionIdGenerator.java
│   │   │   ├── client/
│   │   │   │   ├── InventoryClient.java
│   │   │   │   ├── InventoryClientFallback.java
│   │   │   │   ├── UserClient.java
│   │   │   │   └── UserClientFallback.java
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── security/
│   │   │   │   ├── JwtFilter.java
│   │   │   │   └── JwtUtil.java
│   │   │   └── BillingServiceApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/java/com/medlab/billing/
│       ├── controller/
│       │   ├── BillingControllerTest.java
│       │   └── PaymentControllerTest.java
│       └── service/
│           ├── BillingServiceTest.java
│           └── PaymentServiceTest.java
├── Dockerfile
├── pom.xml
└── README.md
```

---

## What This Service Does

1. **Invoice Generation** — `POST /billing/generate/{orderId}` is called by the Lab Processing Service (P3) when a test result is approved. Fetches test prices from Inventory Service (P4) via FeignClient, calculates the total, persists the invoice as `PENDING`, and notifies the patient via User Service (P1).
2. **Mocked Payment** — `POST /payments` accepts a payment method (CREDIT_CARD / DEBIT_CARD / UPI), always returns SUCCESS with a generated transaction ID, marks the invoice as `PAID`, and sends a payment confirmation notification to the patient.
3. **Queries** — Retrieve invoices by ID, order ID, or patient ID; retrieve payment history by invoice ID.

---

## REST API

| Method | Endpoint | Role Required | Description |
|--------|----------|---------------|-------------|
| `POST` | `/billing/generate/{orderId}` | `LAB_TECH`, `ADMIN` | Generate invoice (triggered by Lab Processing) |
| `GET`  | `/invoices/{id}` | Any authenticated | Get invoice by ID |
| `GET`  | `/invoices/order/{orderId}` | Any authenticated | Get invoice by order ID |
| `GET`  | `/invoices/patient/{patientId}` | `ADMIN`, `PHYSICIAN`, `RECEPTIONIST`, `PATIENT` | All invoices for a patient |
| `POST` | `/payments` | `PATIENT`, `ADMIN`, `RECEPTIONIST` | Submit payment (always succeeds — mocked) |
| `GET`  | `/payments/{invoiceId}` | `PATIENT`, `ADMIN`, `RECEPTIONIST`, `PHYSICIAN` | Payment history for an invoice |

---

## Inter-Service Communication

| This service calls | Via | When | Why |
|---|---|---|---|
| Inventory Service (`inventory-service`) | FeignClient `GET /tests/{id}` | Invoice generation | Fetch price per test to calculate total |
| User Service (`user-service`) | FeignClient `POST /notifications` | After invoice created | Notify patient: `INVOICE_GENERATED` |
| User Service (`user-service`) | FeignClient `POST /notifications` | After payment confirmed | Notify patient: `PAYMENT_SUCCESS` |

Both clients have fallbacks — if a downstream service is unreachable, billing and payment flows continue uninterrupted.

---

## Invoice & Payment State Lifecycle

```
Invoice:  PENDING ──► PAID
                 └──► CANCELLED

Payment:  (always written as PAID — mocked gateway)
```

---

## Running Standalone (No Other Services)

```bash
# 1. Start MySQL
docker run -d --name billing-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=billing \
  -p 3306:3306 mysql:8.0

# 2. In application.yml, comment out or disable:
#      spring.config.import: ""
#      eureka.client.enabled: false

# 3. Run
mvn spring-boot:run

# Swagger UI
open http://localhost:8085/swagger-ui.html
```

---

## Running Tests

```bash
# All tests (unit — no Docker required)
mvn test

# Build and test
mvn clean verify
```

### Test Coverage

| Class | Type | Tests |
|---|---|---|
| `service/BillingServiceTest` | Unit (Mockito) | 11 tests — invoice generation, notification, idempotency, markPaid state machine |
| `service/PaymentServiceTest` | Unit (Mockito) | 8 tests — mocked payment, all payment methods, notification resilience, payment history |
| `controller/BillingControllerTest` | `@WebMvcTest` | 9 tests — role enforcement, 201/400/403/404/409 responses |
| `controller/PaymentControllerTest` | `@WebMvcTest` | 10 tests — role enforcement, 201/400/403/404/422 responses, all payment methods |

---

## Full System Startup Order

```
Config Server (8888)
  → Eureka Discovery (8761)
    → User Service (8081)
    → Inventory Service (8084)
    → Billing Service (8085)   ← this service
  → API Gateway (8080)
```

Billing Service registers with Eureka automatically. FeignClients resolve `inventory-service` and `user-service` by name through Eureka.

---

## Key Design Notes

| Decision | Detail |
|---|---|
| Mocked payment | No real gateway — `POST /payments` always writes `PAID` |
| Idempotent invoice generation | One invoice per `orderId`; duplicate call returns `409 Conflict` |
| Notification resilience | `UserClientFallback` swallows failures silently — billing never blocks |
| JWT interop | Same secret and `JwtUtil` logic as P1 (User Service) |
| Claim schema | `claims` table created by Hibernate; no active controller or service logic |