package com.backend.dto;

import java.time.Instant;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MachineStatsResponse {
    private String machineId;
    private Instant start;
    private Instant end;

    private long eventsCount;
    private long defectsCount;
    private double avgDefectRate;
    private String status; // Healthy / Warning
}
