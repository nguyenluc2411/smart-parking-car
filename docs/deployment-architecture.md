# Deployment Architecture – Smart Parking System

## Phase 1: Docker Compose

### Container Map

| Container | Image | Port host:container | Network |
|---|---|---|---|
| admin-db | postgres:16-alpine | 5433:5432 | parking-net (internal only) |
| parking-db | postgres:16-alpine | 5434:5432 | parking-net (internal only) |
| billing-db | postgres:16-alpine | 5435:5432 | parking-net (internal only) |
| kafka | confluentinc/cp-kafka:7.6.0 | 9092:9092 | parking-net |
| admin-service | smart-parking/admin:${VERSION} | 8083:8083 | parking-net |
| parking-service | smart-parking/parking:${VERSION} | 8081:8081 | parking-net |
| billing-service | smart-parking/billing:${VERSION} | 8082:8082 | parking-net |
| edge-agent | smart-parking/edge:${VERSION} | 8000:8000 | parking-net |
| admin-dashboard | smart-parking/dashboard:${VERSION} | 3000:3000 | parking-net |
| prometheus | prom/prometheus:v2.51.0 | 9090:9090 | parking-net |
| grafana | grafana/grafana:10.4.0 | 3001:3000 | parking-net |
| alertmanager | prom/alertmanager:v0.27.0 | 9093:9093 | parking-net |
| node-exporter | prom/node-exporter:v1.8.0 | 9100:9100 | parking-net |
| kafka-exporter | danielqsj/kafka-exporter | 9308:9308 | parking-net |

### Startup Order (depends_on condition: service_healthy)
```
Level 0: admin-db, parking-db, billing-db, kafka
Level 1: admin-service, parking-service, billing-service
Level 2: edge-agent, admin-dashboard
Level 3: prometheus, alertmanager, node-exporter, kafka-exporter
Level 4: grafana
```

### Environment Variables (.env)
```bash
# Version
VERSION=1.0.0

# Passwords (thay bằng giá trị thực)
ADMIN_DB_PASSWORD=change_me_admin
PARKING_DB_PASSWORD=change_me_parking
BILLING_DB_PASSWORD=change_me_billing

# JWT
JWT_SECRET=change_me_jwt_secret_min_256_bits

# Edge Agent
EDGE_API_KEY=change_me_edge_api_key
ALPR_CONFIDENCE=0.85

# Grafana
GF_SECURITY_ADMIN_PASSWORD=change_me_grafana
```

---

## Phase 2: Kubernetes

### Namespace
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: smart-parking-car
```

### Per-service manifest files
```
k8s/
├── namespace.yaml
├── admin-service/
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
├── parking-service/
│   └── ... (same structure)
├── billing-service/
│   └── ...
├── edge-agent/
│   └── ...
├── databases/
│   ├── admin-db/
│   │   ├── pvc.yaml
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   └── ... (parking-db, billing-db)
├── kafka/
│   └── ... (StatefulSet)
└── monitoring/
    ├── prometheus/
    └── grafana/
```

### Resource Limits (k8s)
| Service | CPU request | CPU limit | Memory request | Memory limit |
|---|---|---|---|---|
| admin-service | 100m | 500m | 256Mi | 512Mi |
| parking-service | 200m | 750m | 256Mi | 768Mi |
| billing-service | 100m | 500m | 256Mi | 512Mi |
| edge-agent | 500m | 2000m | 512Mi | 2048Mi |
| PostgreSQL (each) | 100m | 500m | 256Mi | 512Mi |
| Kafka | 500m | 1000m | 512Mi | 1024Mi |
