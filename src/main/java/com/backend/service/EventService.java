package com.backend.service;

import com.backend.dao.EventBatchDao;
import com.backend.dto.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final long MAX_DURATION_MS = Duration.ofHours(6).toMillis();
    private static final Duration FUTURE_ALLOWANCE = Duration.ofMinutes(15);

    private final EventBatchDao eventBatchDao;
    private final Clock clock;
    private final Validator validator;

    @Transactional
    public BatchIngestResponse ingestBatch(List<EventIngestRequest> requests) {
        BatchIngestResponse resp = BatchIngestResponse.builder().build();
        if (requests == null || requests.isEmpty()) {
            return resp;
        }

        Instant now = Instant.now(clock);

        // 1) Validate + map to rows
        List<EventBatchDao.EventRow> validRows = new ArrayList<>(requests.size());
        for (EventIngestRequest r : requests) {
            Optional<RejectionReason> reason = validate(r, now);
            if (reason.isPresent()) {
                reject(resp, r, reason.get());
                continue;
            }

            // Ignore client receivedTime; set server-side time
            Instant receivedTime = Instant.now(clock);

            validRows.add(new EventBatchDao.EventRow(
                    r.getEventId(),
                    r.getFactoryId(),
                    r.getLineId(),
                    r.getMachineId(),
                    r.getEventTime(),
                    receivedTime,
                    r.getDurationMs(),
                    r.getDefectCount()
            ));
        }

        if (validRows.isEmpty()) return resp;

        // 2) Insert new events (conflicts -> count=0)
        int[] insertCounts = eventBatchDao.batchInsertIgnoreConflicts(validRows);

        long accepted = 0;
        List<EventBatchDao.EventRow> conflicted = new ArrayList<>();
        for (int i = 0; i < insertCounts.length; i++) {
            if (insertCounts[i] > 0) accepted++;
            else conflicted.add(validRows.get(i));
        }

        // 3) Update conflicted events if newer receivedTime AND payload differs
        long updated = 0;
        if (!conflicted.isEmpty()) {
            int[] updateCounts = eventBatchDao.batchConditionalUpdate(conflicted);
            for (int c : updateCounts) updated += Math.max(0, c);
        }

        long valid = validRows.size();
        long deduped = valid - accepted - updated;

        resp.setAccepted(resp.getAccepted() + accepted);
        resp.setUpdated(resp.getUpdated() + updated);
        resp.setDeduped(resp.getDeduped() + deduped);

        return resp;
    }

    private void reject(BatchIngestResponse resp, EventIngestRequest r, RejectionReason reason) {
        resp.getRejections().add(RejectionDto.builder()
                .eventId(r == null ? null : r.getEventId())
                .reason(reason)
                .build());
        resp.setRejected(resp.getRejected() + 1);
    }

    private Optional<RejectionReason> validate(EventIngestRequest r, Instant now) {
        if (r == null) return Optional.of(RejectionReason.INVALID_REQUEST);

        Set<ConstraintViolation<EventIngestRequest>> violations = validator.validate(r);
        if (!violations.isEmpty()) return Optional.of(RejectionReason.INVALID_REQUEST);

        long d = r.getDurationMs();
        if (d < 0 || d > MAX_DURATION_MS) return Optional.of(RejectionReason.INVALID_DURATION);

        Instant eventTime = r.getEventTime();
        if (eventTime.isAfter(now.plus(FUTURE_ALLOWANCE))) return Optional.of(RejectionReason.FUTURE_EVENT_TIME);

        return Optional.empty();
    }
}
