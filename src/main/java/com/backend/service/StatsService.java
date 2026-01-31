package com.backend.service;

import com.backend.dto.MachineStatsResponse;
import com.backend.dto.TopDefectLineDto;
import com.backend.repository.EventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;

    public MachineStatsResponse machineStats(String machineId, Instant start, Instant end) {
        validateWindow(machineId, start, end, "machineId/start/end");

        var agg = eventRepository.aggregateMachineStats(machineId, start, end);

        long eventsCount = (agg == null) ? 0 : agg.getEventsCount();
        long defectsCount = (agg == null) ? 0 : agg.getDefectsCount();

        double hours = Duration.between(start, end).toSeconds() / 3600.0;
        double avgDefectRate = (hours <= 0.0) ? 0.0 : (defectsCount / hours);

        return MachineStatsResponse.builder()
                .machineId(machineId)
                .start(start)
                .end(end)
                .eventsCount(eventsCount)
                .defectsCount(defectsCount)
                .avgDefectRate(round(avgDefectRate, 2))
                .status(avgDefectRate < 2.0 ? "Healthy" : "Warning")
                .build();
    }

    public List<TopDefectLineDto> topDefectLines(String factoryId, Instant from, Instant to, int limit) {
        validateWindow(factoryId, from, to, "factoryId/from/to");
        int safeLimit = clampLimit(limit);

        // Primary ranking: totalDefects DESC (defectsPercent is only context)
        var rows = eventRepository.findTopLinesByTotalDefects(factoryId, from, to, safeLimit);

        return rows.stream().map(r -> {
            long eventCount = r.getEventCount();
            long totalDefects = r.getTotalDefects();
            double pct = (eventCount == 0) ? 0.0 : (totalDefects * 100.0 / eventCount);

            return TopDefectLineDto.builder()
                    .lineId(r.getLineId())
                    .eventCount(eventCount)
                    .totalDefects(totalDefects)
                    .defectsPercent(round(pct, 2))
                    .build();
        }).toList();
    }

    private static void validateWindow(String id, Instant start, Instant end, String label) {
        if (id == null || id.isBlank() || start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Invalid " + label);
        }
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) return 10;
        return Math.min(limit, 100);
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
