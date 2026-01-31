package com.backend.dto;

import java.time.Instant;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EventIngestRequest {

    @NotBlank
    private String eventId;

    @NotBlank
    private String factoryId;

    @NotBlank
    private String lineId;

    @NotBlank
    private String machineId;

    @NotNull
    private Instant eventTime;

    /**
     * Client may send it, but we ignore it and set receivedTime on server.
     */
    private Instant receivedTime;

    private long durationMs;

    private int defectCount;
}
