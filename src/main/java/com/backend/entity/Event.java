package com.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "event",
        indexes = {
                @Index(name = "idx_event_machine_time", columnList = "machine_id,event_time"),
                @Index(name = "idx_event_factory_line_time", columnList = "factory_id,line_id,event_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "factory_id", nullable = false)
    private String factoryId;

    @Column(name = "line_id", nullable = false)
    private String lineId;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "received_time", nullable = false)
    private Instant receivedTime;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "defect_count", nullable = false)
    private int defectCount;
}
