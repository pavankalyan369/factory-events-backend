# BENCHMARKS

The goal of this benchmark is to measure the end-to-end ingestion performance of the batch ingestion API (POST /events/batch) when processing 1,000 machine events in a single request.

The benchmark focuses only on the ingestion pipeline:
- Validation
- Deduplication
- Conditional updates
- Database persistence


---

## Test Environment

- CPU: 13th Gen Intel® Core™ i7-13620H @ 2.40 GHz
- RAM: 16.0 GB
- Operating System: Windows 11 Home (64-bit)
- Java Version: Java 21 (LTS)
- Spring Boot Version: 3.5.10
- Database: PostgreSQL (running locally via Docker)

---

## Benchmark Setup
### Prerequisites
- Java 21
- Maven
- Docker
### Database Startup

PostgreSQL was started locally using Docker Compose:
```bash
docker-compose up -d
```
### Application Startup
The application was started locally using Spring Boot:
```bash
mvn spring-boot:run
```

OR (Alternative command on Windows)
```bash
.\mvnw -Dmaven.clean.failOnError=false clean spring-boot:run
```

### Storage Mode

- Database: PostgreSQL
- Deployment: Local Docker container
- Schema initialized via schema.sql
- No pre-existing data in the event table before the benchmark

### Preconditions

Before running the benchmark:
- Database was empty
- Application was fully started and ready to accept requests

---

## Benchmark Execution

### Ingestion Method

- Endpoint: POST /events/batch
- Payload: JSON array containing exactly 1,000 events

Five ingestion runs were executed using deterministic Postman pre-request scripts, each representing a different workload pattern:

- Run 1: Mixed realistic workload (inserts, deduplication, updates, rejects)
- Run 2: All valid, all unique events (pure insert throughput)
- Run 3: Deduplication and validation heavy workload (more than 500 non-insert paths)
- Run 4: Update-heavy workload
- Run 5: Validation-only heavy workload

### Execution Tool

- Tool: Postman
- Method: JavaScript pre-request scripts generating deterministic 1,000-event batches
- Measurement: End-to-end request duration reported by Postman
- Command (for reference):
```bash
POST http://localhost:8080/events/batch
Content-Type: application/json
Body: {{batchEvents}}
```
- Where batchEvents is populated by the Postman pre-request script.
---

## Results

### Observed Performance

Measured end-to-end ingestion time per batch of 1,000 events:

- Run 1 (Mixed realistic workload): 154 ms
- Run 2 (All valid, all unique): 203 ms
- Run 3 (Dedup + validation heavy): 208 ms
- Run 4 (Update-heavy): 172 ms
- Run 5 (Validation-only heavy): 259 ms

Performance requirement (< 1 second): YES — met in all runs.

### Variance Across Runs

- Minimum time: 154 ms
- Maximum time: 259 ms
- Average time: approximately 199 ms

---

## Observations

- Ingestion latency remained consistently low across all scenarios.
- Deduplication- and validation-heavy workloads did not significantly degrade performance.
- Update-heavy ingestion showed comparable latency to pure insert workloads.
- Even worst-case validation-heavy scenarios completed well under the 1-second requirement.
- No noticeable latency spikes were observed across runs.

These results indicate that non-happy-path processing (validation failures, duplicates, and updates) does not materially impact end-to-end ingestion performance.

---

## Optimizations Used

The following ingestion-focused optimizations are present in the implementation:

- Batch JDBC inserts (batchUpdate) instead of per-row persistence
- INSERT ... ON CONFLICT DO NOTHING for efficient deduplication
- Conditional updates executed only when conflicts occur
- Minimal object allocation during ingestion
- Database indexes aligned with ingestion access patterns

---

## Reproducibility

To reproduce this benchmark:

1. Ensure Docker is running
2. Start PostgreSQL using docker-compose up -d
3. Start the Spring Boot application locally
4. Send a POST request with exactly 1,000 events to /events/batch
5. Measure the end-to-end request duration

---

Note:
All performance numbers in this document are directly measured from the described benchmark runs. No performance claims are made beyond the observed results.
