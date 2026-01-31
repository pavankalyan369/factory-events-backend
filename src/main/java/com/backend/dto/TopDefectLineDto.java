package com.backend.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TopDefectLineDto {
    private String lineId;
    private long totalDefects;
    private long eventCount;
    private double defectsPercent; // rounded to 2 decimals
}
