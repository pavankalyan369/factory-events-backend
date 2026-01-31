package com.backend;

import com.backend.dto.BatchIngestResponse;
import com.backend.dto.EventIngestRequest;
import com.backend.repository.EventRepository;
import com.backend.testutil.MutableClock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(FactoryEventsApplicationTests.TestClockConfig.class)
class EventSystemIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("factory_events")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired EventRepository repo;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        clock.set(Instant.parse("2026-01-15T00:00:00Z"));
    }

    private String postBatch(List<EventIngestRequest> req) throws Exception {
        return mvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private EventIngestRequest baseEvent(String eventId, Instant eventTime) {
        return EventIngestRequest.builder()
                .eventId(eventId)
                .factoryId("F-01")
                .lineId("L-01")
                .machineId("M-001")
                .eventTime(eventTime)
                .receivedTime(Instant.parse("2099-01-01T00:00:00Z")) // ignored by server
                .durationMs(1000)
                .defectCount(0)
                .build();
    }

    // 1) Identical duplicate eventId → deduped
    @Test
    void test1_identicalDuplicateEventId_isDeduped() throws Exception {
        var e = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));

        BatchIngestResponse r1 = om.readValue(postBatch(List.of(e)), BatchIngestResponse.class);
        BatchIngestResponse r2 = om.readValue(postBatch(List.of(e)), BatchIngestResponse.class);

        assertThat(r1.getAccepted()).isEqualTo(1);
        assertThat(r2.getDeduped()).isEqualTo(1);
        assertThat(repo.count()).isEqualTo(1);
    }

    // 2) Different payload + newer receivedTime → update happens
    @Test
    void test2_differentPayload_newerReceivedTime_updates() throws Exception {
        var e1 = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));

        BatchIngestResponse r1 = om.readValue(postBatch(List.of(e1)), BatchIngestResponse.class);
        assertThat(r1.getAccepted()).isEqualTo(1);

        clock.plus(Duration.ofMinutes(1));
        var e2 = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        e2.setDefectCount(5);

        BatchIngestResponse r2 = om.readValue(postBatch(List.of(e2)), BatchIngestResponse.class);
        assertThat(r2.getUpdated()).isEqualTo(1);

        var stored = repo.findByEventId("E-1").orElseThrow();
        assertThat(stored.getDefectCount()).isEqualTo(5);
    }

    // 3) Different payload + older receivedTime → ignored
    @Test
    void test3_differentPayload_olderReceivedTime_isIgnored() throws Exception {
        clock.set(Instant.parse("2026-01-15T00:05:00Z"));
        var e1 = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        e1.setDefectCount(1);
        postBatch(List.of(e1));

        clock.set(Instant.parse("2026-01-15T00:01:00Z"));
        var e2 = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        e2.setDefectCount(999);

        BatchIngestResponse r2 = om.readValue(postBatch(List.of(e2)), BatchIngestResponse.class);
        assertThat(r2.getUpdated()).isEqualTo(0);

        var stored = repo.findByEventId("E-1").orElseThrow();
        assertThat(stored.getDefectCount()).isEqualTo(1);
    }

    // 4) Invalid duration rejected
    @Test
    void test4_invalidDuration_rejected() throws Exception {
        var e = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        e.setDurationMs(-1);

        BatchIngestResponse r = om.readValue(postBatch(List.of(e)), BatchIngestResponse.class);

        assertThat(r.getRejected()).isEqualTo(1);
        assertThat(r.getRejections()).hasSize(1);
        assertThat(r.getRejections().get(0).getReason().name()).isEqualTo("INVALID_DURATION");
        assertThat(repo.count()).isEqualTo(0);
    }

    // 5) Future eventTime rejected
    @Test
    void test5_futureEventTime_rejected() throws Exception {
        var e = baseEvent("E-1", Instant.parse("2026-01-15T00:16:01Z"));

        BatchIngestResponse r = om.readValue(postBatch(List.of(e)), BatchIngestResponse.class);

        assertThat(r.getRejected()).isEqualTo(1);
        assertThat(r.getRejections()).hasSize(1);
        assertThat(r.getRejections().get(0).getReason().name()).isEqualTo("FUTURE_EVENT_TIME");
        assertThat(repo.count()).isEqualTo(0);
    }

    // 6) DefectCount = -1 ignored in totals
    @Test
    void test6_defectMinus1_storedButExcludedFromDefectTotals() throws Exception {
        var e1 = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        e1.setDefectCount(-1);
        var e2 = baseEvent("E-2", Instant.parse("2026-01-15T00:00:20Z"));
        e2.setDefectCount(3);

        postBatch(List.of(e1, e2));

        MvcResult res = mvc.perform(get("/stats")
                        .param("machineId", "M-001")
                        .param("start", "2026-01-15T00:00:00Z")
                        .param("end", "2026-01-15T01:00:00Z"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = om.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(((Number) body.get("eventsCount")).longValue()).isEqualTo(2);
        assertThat(((Number) body.get("defectsCount")).longValue()).isEqualTo(3);
    }

    // 7) start inclusive / end exclusive
    @Test
    void test7_startInclusive_endExclusive() throws Exception {
        // Move "now" forward so events at 00:59:59Z are NOT considered "too future"
        clock.set(Instant.parse("2026-01-15T02:00:00Z"));

        var start = Instant.parse("2026-01-15T00:00:00Z");
        var end = Instant.parse("2026-01-15T01:00:00Z");

        var eStart = baseEvent("E-1", start); // included
        var eBeforeEnd = baseEvent("E-2", end.minusSeconds(1)); // included
        var eAtEnd = baseEvent("E-3", end); // excluded

        postBatch(List.of(eStart, eBeforeEnd, eAtEnd));

        MvcResult res = mvc.perform(get("/stats")
                        .param("machineId", "M-001")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = om.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(((Number) body.get("eventsCount")).longValue()).isEqualTo(2);
    }


    // 8) Thread-safety test
    @Test
    void test8_threadSafety_concurrentIngestionDoesNotCorrupt() throws Exception {
        var e = baseEvent("E-1", Instant.parse("2026-01-15T00:00:10Z"));
        String payload = om.writeValueAsString(List.of(e));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return mvc.perform(post("/events/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
            }));
        }

        startGate.countDown();

        long acceptedTotal = 0;
        long dedupedTotal = 0;
        long updatedTotal = 0;

        for (Future<String> f : futures) {
            BatchIngestResponse r = om.readValue(f.get(10, TimeUnit.SECONDS), BatchIngestResponse.class);
            acceptedTotal += r.getAccepted();
            dedupedTotal += r.getDeduped();
            updatedTotal += r.getUpdated();
        }

        pool.shutdownNow();

        assertThat(repo.count()).isEqualTo(1);
        assertThat(acceptedTotal).isEqualTo(1);
        assertThat(updatedTotal).isEqualTo(0);
        assertThat(dedupedTotal).isEqualTo(threads - 1);



    }
    // test -9
    @Test
    void test9_topDefectLines_sortedByTotalDefects_desc() throws Exception {
        // Make "now" later so events aren't rejected as future
        clock.set(Instant.parse("2026-01-15T10:00:00Z"));

        var baseT = Instant.parse("2026-01-15T00:00:00Z");

        // Line A: 2 events, total defects = 50
        var a1 = baseEvent("A-1", baseT.plusSeconds(10)); a1.setLineId("L-A"); a1.setDefectCount(40);
        var a2 = baseEvent("A-2", baseT.plusSeconds(20)); a2.setLineId("L-A"); a2.setDefectCount(10);

        // Line B: 10 events, total defects = 30 (but lower total)
        List<EventIngestRequest> batch = new ArrayList<>();
        batch.add(a1); batch.add(a2);
        for (int i = 0; i < 10; i++) {
            var b = baseEvent("B-" + i, baseT.plusSeconds(100 + i));
            b.setLineId("L-B");
            b.setDefectCount(3); // 10 * 3 = 30
            batch.add(b);
        }

        postBatch(batch);

        MvcResult res = mvc.perform(get("/stats/top-defect-lines")
                        .param("factoryId", "F-01")
                        .param("from", "2026-01-15T00:00:00Z")
                        .param("to", "2026-01-15T01:00:00Z")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> body = om.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(body).isNotEmpty();

        // Must rank by totalDefects DESC
        assertThat(body.get(0).get("lineId")).isEqualTo("L-A");
        assertThat(((Number) body.get(0).get("totalDefects")).longValue()).isEqualTo(50L);
    }

}
