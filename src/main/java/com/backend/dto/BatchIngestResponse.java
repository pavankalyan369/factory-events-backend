package com.backend.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BatchIngestResponse {
    private long accepted;
    private long deduped;
    private long updated;
    private long rejected;

    @Builder.Default
    private List<RejectionDto> rejections = new ArrayList<>();
}
