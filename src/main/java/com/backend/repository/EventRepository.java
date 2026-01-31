package com.backend.repository;

import com.backend.entity.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventId(String eventId);

    interface MachineStatsAgg {
        long getEventsCount();
        long getDefectsCount();
    }

    @Query(value = """
        SELECT
          COUNT(*) AS eventsCount,
          COALESCE(SUM(CASE WHEN defect_count = -1 THEN 0 ELSE defect_count END), 0) AS defectsCount
        FROM event
        WHERE machine_id = :machineId
          AND event_time >= :start
          AND event_time < :end
        """, nativeQuery = true)
    MachineStatsAgg aggregateMachineStats(
            @Param("machineId") String machineId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    interface TopLineAgg {
        String getLineId();
        long getEventCount();
        long getTotalDefects();
    }

    @Query(value = """
        SELECT
          line_id AS lineId,
          COUNT(*) AS eventCount,
          COALESCE(SUM(CASE WHEN defect_count = -1 THEN 0 ELSE defect_count END), 0) AS totalDefects
        FROM event
        WHERE factory_id = :factoryId
          AND event_time >= :from
          AND event_time < :to
        GROUP BY line_id
        ORDER BY totalDefects DESC, eventCount DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<TopLineAgg> findTopLinesByTotalDefects(
            @Param("factoryId") String factoryId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit
    );
}
