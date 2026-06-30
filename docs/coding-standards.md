# Coding Standards – Smart Parking System

## Spring Boot Standards

### 1. Package Structure (bắt buộc)
```
com.smartparking.{service}/
├── controller/
├── service/
│   └── impl/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   ├── response/
│   └── event/
├── mapper/
├── config/
└── exception/
```

### 2. Entity Template
```java
@Entity
@Table(name = "sessions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
```

### 3. Service Interface Template
```java
public interface SessionService {
    SessionResponseDTO createSession(CreateSessionRequestDTO dto);
    SessionResponseDTO getSessionById(UUID id);
    Page<SessionResponseDTO> getSessions(SessionFilterDTO filter, Pageable pageable);
    void closeSession(UUID id, Instant exitTime);
}
```

### 4. ServiceImpl Template
```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)     // Default read-only
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final OutboxEventService outboxEventService;

    @Override
    @Transactional                   // Override cho write operations
    public SessionResponseDTO createSession(CreateSessionRequestDTO dto) {
        log.info("Creating session for plate: {}", dto.getPlateNumber());

        // 1. Business validation
        validateNoActiveSession(dto.getPlateNumber());

        // 2. Build entity
        Session session = sessionMapper.toEntity(dto);
        session.setStatus(SessionStatus.PENDING);
        session.setEntryTime(Instant.now());

        // 3. Save
        session = sessionRepository.save(session);

        // 4. Outbox event (trong cùng transaction)
        outboxEventService.save(OutboxEvent.builder()
            .aggregateType("Session")
            .aggregateId(session.getId())
            .eventType("parking.session.created")
            .payload(sessionMapper.toEventDTO(session))
            .build());

        log.info("Session created: id={}, plate={}", session.getId(), session.getPlateNumber());
        return sessionMapper.toResponseDTO(session);
    }

    private void validateNoActiveSession(String plateNumber) {
        sessionRepository.findByPlateNumberAndStatus(plateNumber, SessionStatus.ACTIVE)
            .ifPresent(s -> {
                throw new ConflictException("Plate " + plateNumber + " already has an active session: " + s.getId());
            });
    }
}
```

### 5. Controller Template
```java
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sessions", description = "Session management endpoints")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/{id}")
    @Operation(summary = "Get session by ID")
    public ResponseEntity<ApiResponse<SessionResponseDTO>> getSession(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.getSessionById(id)));
    }
}
```

### 6. GlobalExceptionHandler Template
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(400).body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(ApiResponse.error("INTERNAL_ERROR", "Internal server error"));
    }
}
```

### 7. DTO Validation
```java
public record CreateSessionRequestDTO(
    @NotBlank(message = "Plate number is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]-[0-9]{3,5}(\\.[0-9]{2})?$",
             message = "Invalid VN plate format")
    String plateNumber,

    @NotNull(message = "Gate ID is required")
    UUID gateId
) {}
```

### 8. MapStruct Mapper
```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SessionMapper {
    SessionResponseDTO toResponseDTO(Session session);
    Session toEntity(CreateSessionRequestDTO dto);
    SessionCreatedEventDTO toEventDTO(Session session);
}
```

---

## Python / FastAPI Standards

### 1. Project Structure
```
app/
├── main.py
├── routers/
│   ├── detect.py
│   ├── simulate.py
│   └── config.py
├── services/
│   ├── alpr_service.py
│   ├── kafka_service.py
│   └── gate_service.py
├── models/
│   └── schemas.py          # Pydantic models
├── core/
│   ├── config.py           # Settings từ env vars
│   └── logging.py
└── health.py
```

### 2. Pydantic Schema
```python
from pydantic import BaseModel, Field
from typing import Optional
import uuid

class PlateDetectedEvent(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    plate_number: str
    confidence: float = Field(ge=0.0, le=1.0)
    gate_id: str
    direction: str = Field(pattern="^(IN|OUT)$")
    timestamp: str
    processing_ms: int
```

### 3. Settings từ env vars
```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    kafka_bootstrap_servers: str
    parking_service_url: str
    alpr_confidence_threshold: float = 0.85
    api_key: str
    model_path: str = "/app/models/yolov8s_vn.pt"

    class Config:
        env_file = ".env"

settings = Settings()
```

### 4. Logging chuẩn
```python
import logging
import json

logging.basicConfig(
    format='{"time":"%(asctime)s","level":"%(levelname)s","service":"edge-agent","msg":"%(message)s"}',
    level=logging.INFO
)
logger = logging.getLogger(__name__)
```
