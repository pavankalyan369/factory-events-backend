package com.backend.dao;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventBatchDao {

    private final JdbcTemplate jdbcTemplate;

    // Java 21 record = less boilerplate, still works the same for construction
    public record EventRow(
            String eventId,
            String factoryId,
            String lineId,
            String machineId,
            Instant eventTime,
            Instant receivedTime,
            long durationMs,
            int defectCount
    ) {}

    /**
     * Inserts rows using ON CONFLICT DO NOTHING.
     * Returns int[]: 1 if inserted, 0 if conflict/no insert.
     */
    public int[] batchInsertIgnoreConflicts(List<EventRow> rows) {
        if (rows == null || rows.isEmpty()) return new int[0];

        String sql = """
            INSERT INTO event (
              event_id, factory_id, line_id, machine_id, event_time, received_time, duration_ms, defect_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                EventRow r = rows.get(i);
                ps.setString(1, r.eventId());
                ps.setString(2, r.factoryId());
                ps.setString(3, r.lineId());
                ps.setString(4, r.machineId());
                ps.setTimestamp(5, Timestamp.from(r.eventTime()));
                ps.setTimestamp(6, Timestamp.from(r.receivedTime()));
                ps.setLong(7, r.durationMs());
                ps.setInt(8, r.defectCount());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    /**
     * Conditional update for conflicted rows.
     * Update happens only if:
     *  - existing.received_time < incoming.received_time (newer wins), AND
     *  - payload differs (otherwise it is a dedupe)
     *
     * Returns int[]: 1 if updated, 0 otherwise.
     */
    public int[] batchConditionalUpdate(List<EventRow> rows) {
        if (rows == null || rows.isEmpty()) return new int[0];

        String sql = """
            UPDATE event SET
              factory_id = ?,
              line_id = ?,
              machine_id = ?,
              event_time = ?,
              received_time = ?,
              duration_ms = ?,
              defect_count = ?
            WHERE event_id = ?
              AND received_time < ?
              AND (
                factory_id IS DISTINCT FROM ? OR
                line_id   IS DISTINCT FROM ? OR
                machine_id IS DISTINCT FROM ? OR
                event_time IS DISTINCT FROM ? OR
                duration_ms IS DISTINCT FROM ? OR
                defect_count IS DISTINCT FROM ?
              )
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                EventRow r = rows.get(i);

                ps.setString(1, r.factoryId());
                ps.setString(2, r.lineId());
                ps.setString(3, r.machineId());
                ps.setTimestamp(4, Timestamp.from(r.eventTime()));
                ps.setTimestamp(5, Timestamp.from(r.receivedTime()));
                ps.setLong(6, r.durationMs());
                ps.setInt(7, r.defectCount());

                ps.setString(8, r.eventId());
                ps.setTimestamp(9, Timestamp.from(r.receivedTime()));

                // payload compare again
                ps.setString(10, r.factoryId());
                ps.setString(11, r.lineId());
                ps.setString(12, r.machineId());
                ps.setTimestamp(13, Timestamp.from(r.eventTime()));
                ps.setLong(14, r.durationMs());
                ps.setInt(15, r.defectCount());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }
}
