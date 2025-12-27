# CLAUDE.md — Authoritative Rules for Claude Code

This file defines **enforceable rules and invariants**.
It MUST contain policy only.
Architecture, usage, workflows, and explanations MUST live in `docs/`.

## Technology Stack (Locked Versions)

- **Java**: 23 (use modern features: records, pattern matching, virtual threads where appropriate)
- **Spring Boot**: 3.4.5
- **Spring Cloud**: 2024.0.0 — **Config Server ONLY** (no other Spring Cloud components!)
- **Lombok**: 1.18.36
- **Database**: PostgreSQL with Spring Data JPA
- **Migrations**: Flyway
- **Inter-service communication**: WebClient (REST)
- **API Documentation**: SpringDoc OpenAPI
- **Testing**: JUnit 5 + Mockito + Testcontainers
- **Runtime**: Kubernetes (no service mesh)
- **Observability**: OpenSearch

## Code Style & Engineering Principles

- Act as a senior Java/Spring Boot engineer
- Follow **Google Java Style Guide**
- Use modern Java 23 features where they improve clarity
- Code must be self-documenting
- Single Responsibility Principle is mandatory
- Class size ≤ **300 LOC** (excluding imports and annotations)
- Method size ≤ **40 LOC**
- Public methods must be declared before private ones
- Prefer clarity, testability, and determinism over cleverness
- Apply **KISS / DRY / YAGNI**
- Extract magic numbers and strings to named constants

## Multi-Tier Architecture

Services are organized into three tiers:

| Tier      | Purpose                             | Exposed To             | Authorization         | Module Structure  |
| --------- | ----------------------------------- | ---------------------- | --------------------- | ----------------- |
| **tier1** | External API for web/mobile clients | Public (via gateway)   | Required (JWT/OAuth2) | `service` only    |
| **tier2** | Internal business services          | Internal services only | Not required          | `api` + `service` |
| **tier3** | External system integrations        | Internal services only | Not required          | `api` + `service` |

### Module Structure (tier2 & tier3)

Multi-module Maven project:

```
my-service/
├── pom.xml                          # Parent POM
├── api/
│   ├── pom.xml
│   └── src/main/java/com/company/myservice/api/
│       ├── MyServiceApi.java        # Interface with Spring MVC annotations
│       ├── MyServiceClient.java     # WebClient implementation of the interface
│       ├── dto/
│       │   └── UserDto.java
│       ├── request/
│       │   └── CreateUserRequest.java
│       └── response/
│           └── UserResponse.java
└── service/
    ├── pom.xml                      # Depends on api module
    └── src/main/java/com/company/myservice/
        ├── controller/
        │   └── MyServiceController.java  # Implements MyServiceApi
        ├── service/
        ├── repository/
        ├── entity/
        ├── mapper/
        ├── config/
        └── exception/
```

### Module Structure (tier1)

Single module (no shared API):

```
my-gateway-service/
├── pom.xml
└── src/main/java/com/company/gateway/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    ├── dto/
    ├── request/
    ├── response/
    ├── mapper/
    ├── config/
    └── exception/
```

### API Interface Pattern (tier2 & tier3)

The API interface defines the contract with Spring MVC annotations.
Both controller and client implement the same interface for consistency.

```java
// api module: Interface with full Spring MVC annotations
public interface UserApi {
    
    @GetMapping("/{id}")
    UserResponse getUser(@PathVariable("id") Long id);
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createUser(@RequestBody @Valid CreateUserRequest request);
}

// service module: Controller implements interface
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {
    
    private final UserService userService;
    
    @Override
    public UserResponse getUser(Long id) {
        return userService.getById(id);
    }
    
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        return userService.create(request);
    }
}

// api module: Client implements same interface
@Component
@RequiredArgsConstructor
public class UserClient implements UserApi {
    
    private final WebClient userServiceWebClient;
    
    @Override
    public UserResponse getUser(Long id) {
        return userServiceWebClient.get()
            .uri("/{id}", id)
            .retrieve()
            .bodyToMono(UserResponse.class)
            .block();
    }
    
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        return userServiceWebClient.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(UserResponse.class)
            .block();
    }
}
```

### API Module Rules

**MUST contain**:

- API interface with Spring MVC annotations
- Client implementation (WebClient-based)
- All DTOs, requests, and responses used in the interface

**MUST NOT contain**:

- Business logic
- Entity classes
- Repository interfaces
- Spring configuration (except client configuration)

**Dependency direction**:

- `service` module depends on `api` module
- Other services depend on `api` module only
- `api` module has NO dependency on `service`

### Consuming External Service API

```java
// In consuming service's pom.xml
<dependency>
    <groupId>com.company</groupId>
    <artifactId>user-service-api</artifactId>
    <version>${user-service.version}</version>
</dependency>

// In consuming service's configuration
@Configuration
public class UserServiceConfig {
    
    @Bean
    public WebClient userServiceWebClient(WebClient.Builder builder,
                                          @Value("${services.user-service.url}") String baseUrl) {
        return builder
            .baseUrl(baseUrl)
            .build();
    }
    
    @Bean
    public UserClient userClient(WebClient userServiceWebClient) {
        return new UserClient(userServiceWebClient);
    }
}

// In consuming service's code
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final UserClient userClient;  // From user-service-api
    
    public void processOrder(Long userId) {
        UserResponse user = userClient.getUser(userId);
        // ...
    }
}
```

## Package Structure (Layered Architecture)

Within the `service` module (or single module for tier1):

```
src/main/java/com/company/servicename/
├── controller/      # REST controllers (implement API interface for tier2/3)
├── service/         # Business logic
├── repository/      # Data access (Spring Data JPA)
├── entity/          # JPA entities
├── dto/             # Internal DTOs (not exposed via API)
├── mapper/          # Entity ↔ DTO mappers
├── config/          # Spring configuration classes
├── exception/       # Custom exceptions and handlers
└── util/            # Stateless utility classes (minimize usage)
```

For tier1 services (no api module), add:

```
├── request/         # API request records
└── response/        # API response records
```

Layer dependencies (strict):

- `controller` → `service`, `dto`, `request`, `response`
- `service` → `repository`, `entity`, `dto`, `client`
- `repository` → `entity`
- `mapper` → `entity`, `dto`

**FORBIDDEN**:

- Controllers accessing repositories directly
- Entities exposed in API responses
- Circular dependencies between packages
- Business logic in `api` module

## Lombok Usage Rules

**Allowed annotations**:

- `@Getter`, `@Setter` — on entities only
- `@RequiredArgsConstructor` — preferred for dependency injection
- `@Builder` — on DTOs and complex objects
- `@Slf4j` — for logging
- `@ToString(exclude = ...)` — exclude sensitive fields
- `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` — for entities with `@Id`

**FORBIDDEN annotations**:

- `@Data` — too implicit, use explicit annotations
- `@AllArgsConstructor` on entities — breaks JPA
- `@EqualsAndHashCode` on entities without explicit `@Id` inclusion
- `@SneakyThrows` — hides exception handling

## Class Design Rules

### DTOs

- Use Java `record` for all DTOs (Request/Response objects)
- Records are immutable by design — no additional annotations needed
- Use `@Builder` via Lombok's `@Builder` on records when construction is complex

```java
public record UserResponse(
    Long id,
    String email,
    @NonNull String displayName
) {}
```

### Entities

- Always define explicit `@Id` generation strategy
- Use `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@Id` field
- Never use Lombok `@Data` on entities
- Prefer `FetchType.LAZY` for all associations
- Use `@Version` for optimistic locking where needed

```java
@Entity
@Table(name = "users")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    // fields...
}
```

### Services

- Use `@RequiredArgsConstructor` for constructor injection
- All dependencies must be `private final`
- Mark class as `@Service`
- Use `@Transactional` at method level, not class level
- Specify `@Transactional(readOnly = true)` for read operations

### Repositories

- Extend `JpaRepository<Entity, IdType>`
- Use method naming conventions for simple queries
- Use `@Query` with JPQL for complex queries
- Prefer projections over full entity fetches for read-only operations

## Null-Safety Strategy

- Use `@NonNull` and `@Nullable` annotations (from `org.springframework.lang`)
- Apply `@NonNull` to all parameters and return types by default
- Use `@Nullable` explicitly where null is valid
- Package-level `@NonNullApi` in `package-info.java` is RECOMMENDED

```java
@NonNullApi
@NonNullFields
package com.company.service;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
```

**FORBIDDEN**:

- Returning `null` from service methods without `@Nullable`
- Using `Optional` as method parameter
- Using `Optional` as entity field

**Allowed**:

- `Optional<T>` as return type from repository methods
- `Optional<T>` as return type from service methods for "find" operations

## Exception Handling

All custom exceptions MUST extend `RuntimeException`.

### Exception Hierarchy

```java
// Base exception for the service
public class ServiceException extends RuntimeException {
    public ServiceException(String message) { super(message); }
    public ServiceException(String message, Throwable cause) { super(message, cause); }
}

// Specific exceptions
public class EntityNotFoundException extends ServiceException { }
public class BusinessRuleViolationException extends ServiceException { }
public class ExternalServiceException extends ServiceException { }
```

### Global Exception Handler

- Single `@RestControllerAdvice` class per service
- Map exceptions to appropriate HTTP status codes
- Return consistent error response format
- Log exceptions with correlation ID

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        // implementation
    }
}
```

**FORBIDDEN**:

- Catching generic `Exception` without re-throwing
- Swallowing exceptions silently
- Returning stack traces in production responses
- Using checked exceptions in business logic

## REST API Design

### Controller Rules

- One controller per aggregate root
- Use `@RestController` and `@RequestMapping` at class level
- Use specific HTTP method annotations: `@GetMapping`, `@PostMapping`, etc.
- Always specify `produces = MediaType.APPLICATION_JSON_VALUE`
- Return `ResponseEntity<T>` for control over status codes
- Use `@Valid` for request body validation

### URL Conventions

- Use kebab-case: `/user-profiles`, not `/userProfiles`
- Use plural nouns: `/users`, not `/user`
- Nest resources logically: `/users/{id}/orders`
- API versioning in path: `/api/v1/users`

### Response Conventions

- `200 OK` — successful GET, PUT, PATCH
- `201 Created` — successful POST with `Location` header
- `204 No Content` — successful DELETE
- `400 Bad Request` — validation errors
- `404 Not Found` — entity not found
- `409 Conflict` — business rule violation
- `500 Internal Server Error` — unexpected errors

## WebClient (Inter-Service Communication)

### Client Location

- For **tier2/tier3 services**: Client resides in `api` module, implements API interface
- For **external APIs** (not our services): Create client in `service` module under `client/` package

### WebClient Configuration (in consuming service)

```java
@Configuration
public class UserServiceClientConfig {
    
    @Bean
    public WebClient userServiceWebClient(
            WebClient.Builder builder,
            @Value("${services.user-service.url}") String baseUrl) {
        
        return builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter())
            .build();
    }
    
    @Bean
    public UserClient userClient(WebClient userServiceWebClient) {
        return new UserClient(userServiceWebClient);
    }
    
    private ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                request = ClientRequest.from(request)
                    .header("X-Correlation-ID", correlationId)
                    .build();
            }
            return next.exchange(request);
        };
    }
}
```

### Timeout Configuration (mandatory)

```yaml
# application.yml
spring:
  webflux:
    client:
      connect-timeout: 5000
      read-timeout: 10000
      
services:
  user-service:
    url: http://user-service:8080/api/v1/users
    timeout:
      connect: 5s
      read: 10s
```

### Error Handling in Client

```java
// In api module client implementation
@Override
public UserResponse getUser(Long id) {
    return webClient.get()
        .uri("/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
        .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxError)
        .bodyToMono(UserResponse.class)
        .block();
}

private Mono<? extends Throwable> handle4xxError(ClientResponse response) {
    return response.bodyToMono(ErrorResponse.class)
        .map(error -> new ClientException(error.getMessage()));
}
```

**FORBIDDEN**:

- Using WebClient directly in service layer (use typed clients)
- Missing timeout configuration
- Missing error handling in clients
- Missing correlation ID propagation

## Database & JPA Rules

### Flyway Migrations

- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V1__create_users_table.sql`)
- NEVER modify existing migrations
- Use repeatable migrations (`R__`) for views and functions only

### JPA Best Practices

- Use `@EntityGraph` to solve N+1 problems
- Prefer `JPQL` over native queries
- Use `@Modifying` + `@Query` for bulk operations
- Always use parameterized queries — NEVER concatenate values

**FORBIDDEN**:

- `CascadeType.ALL` or `CascadeType.REMOVE` without explicit approval
- `FetchType.EAGER` on collections
- Bidirectional relationships without proper `mappedBy`
- Native queries without `nativeQuery = true` flag

## Configuration Management

### Property Sources Priority

1. Environment variables (Kubernetes ConfigMaps/Secrets)
2. Spring Cloud Config Server
3. `application.yml` (defaults only)

### Profile Structure

- `application.yml` — common defaults
- `application-local.yml` — local development
- `application-test.yml` — test configuration
- Production config via Config Server + environment variables

### Sensitive Data

- NEVER commit secrets to repository
- Use Kubernetes Secrets for credentials
- Reference via `${VARIABLE_NAME}` in properties

**FORBIDDEN**:

- Hardcoded credentials, API keys, or secrets
- Profile-specific files for production (`application-prod.yml`)
- Logging sensitive data

## Kubernetes-Specific Rules

### Health Endpoints

- Configure actuator endpoints properly:
    - `/actuator/health/liveness` — for `livenessProbe`
    - `/actuator/health/readiness` — for `readinessProbe`
- Implement custom health indicators for critical dependencies

### Graceful Shutdown

- Enable graceful shutdown in properties:

  ```yaml
  server:
    shutdown: graceful
  spring:
    lifecycle:
      timeout-per-shutdown-phase: 30s
  ```

- Handle `SIGTERM` properly

- Complete in-flight requests before termination

### Resource Awareness

- Do NOT hardcode thread pool sizes
- Use container-aware defaults or externalize configuration
- Log startup configuration for debugging

### Statelessness

- Services MUST be stateless
- No local file storage for persistent data
- No in-memory caches without external backing (Redis, etc.)
- Session data in external store if needed

**FORBIDDEN**:

- Local file system for persistent data
- `@Scheduled` tasks without distributed locking
- Assumptions about hostname or IP stability

## Observability & Logging

### Logging Rules

- Use SLF4J via Lombok's `@Slf4j`
- Log in JSON format for OpenSearch
- Include correlation ID in all log entries
- Use appropriate log levels:
    - `ERROR` — unexpected failures requiring attention
    - `WARN` — recoverable issues, degraded functionality
    - `INFO` — business events, state changes
    - `DEBUG` — detailed technical information

### Structured Logging

```java
log.info("User created: userId={}, email={}", user.getId(), user.getEmail());
```

### Correlation ID

- Propagate `X-Correlation-ID` header across services
- Use MDC for logging context
- Generate if not present in incoming request

**FORBIDDEN**:

- `System.out.println` or `System.err.println`
- Logging sensitive data (passwords, tokens, PII)
- Excessive logging in hot paths

## Testing Rules

### Test Structure

```
src/test/java/
├── unit/           # Fast, isolated tests
├── integration/    # Tests with real dependencies
└── architecture/   # ArchUnit tests
```

### Unit Tests

- Test service layer logic in isolation
- Mock all dependencies with Mockito
- Naming: `{MethodName}_Should{ExpectedBehavior}_When{Condition}`
- One assertion concept per test (multiple asserts allowed if testing same concept)

### Integration Tests

- Use `@SpringBootTest` sparingly — prefer slices
- Use `@DataJpaTest` for repository tests
- Use `@WebMvcTest` for controller tests
- Use Testcontainers for PostgreSQL

```java
@DataJpaTest
@Testcontainers
class UserRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    // tests...
}
```

### Test Configuration

- Separate `application-test.yml` with test-specific settings
- Use `@TestConfiguration` for test-specific beans
- Disable unnecessary auto-configurations in tests

**FORBIDDEN**:

- Tests depending on execution order
- Tests modifying shared state without cleanup
- Ignoring flaky tests without investigation
- `@SpringBootTest` for testing single layer

## API Documentation (SpringDoc OpenAPI)

- Document all public endpoints
- Use `@Operation` for endpoint description
- Use `@ApiResponse` for response codes
- Use `@Schema` on DTOs for field descriptions
- Keep documentation in sync with code

```java
@Operation(summary = "Get user by ID", description = "Returns user details")
@ApiResponse(responseCode = "200", description = "User found")
@ApiResponse(responseCode = "404", description = "User not found")
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    // implementation
}
```

## Design Patterns (Recommended)

- **Builder** — for complex object construction (via Lombok or manual)
- **Factory** — for creating related objects
- **Strategy** — for interchangeable algorithms
- **Template Method** — for common processing flows
- **Decorator** — for extending behavior (e.g., caching, logging)

**Use with caution**:

- **Singleton** — prefer Spring-managed beans
- **Observer** — prefer Spring Events

**AVOID**:

- Over-engineering with unnecessary patterns
- Patterns that obscure simple logic

## Documentation Maintenance Rule (IMPORTANT)

When modifying code structure, workflows, or architecture, Claude MUST:

- Identify affected documentation files in `docs/`
- Update them to stay consistent with code changes
- Create new documentation files if a new architectural concept is introduced

Code and documentation MUST evolve together.
Outdated documentation is considered a defect.

## MCP (Model Context Protocol) Rules — Authoritative

MCP provides **capabilities only**, not autonomy.

**Allowed MCP servers**:

- Filesystem: read/write within project root
- Git: read-only (diff, blame, history)
- SQLite database: read-only, dev/test only

**Claude MUST**:

- Modify only files relevant to the task
- Preserve architecture unless explicitly instructed
- Follow layered architecture strictly
- Maintain Lombok usage rules

**Claude MUST NOT**:

- Commit or push via Git
- Write to databases
- Use production databases
- Use external HTTP / network MCP servers
- Use documentation/context-fetching MCP servers
- Trigger CI/CD or deployments
- Act as an autonomous agent or planner
- Add dependencies without explicit request

## Determinism & Reproducibility

- Determinism > convenience
- Prefer local, version-controlled context
- Do NOT introduce or update dependencies unless explicitly requested
- Do NOT rely on undocumented external behavior
- Pin dependency versions explicitly in `pom.xml`

## Security Rules

- NEVER hardcode credentials, API keys, or secrets
- Use parameterized queries — NEVER concatenate SQL
- Validate all input at controller level
- Sanitize data before logging
- Follow OWASP guidelines for web security
- Use Spring Security for authentication/authorization

## Rule Priority

1. System / IDE instructions
2. This `CLAUDE.md`
3. Explicit user instructions
4. MCP capabilities

**If a rule is defined here, it overrides all tool behavior.**