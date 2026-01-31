package com.backend.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RejectionDto {
    private String eventId;
    private RejectionReason reason;
}
