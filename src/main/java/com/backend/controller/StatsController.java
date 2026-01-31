package com.backend.controller;

import com.backend.dto.MachineStatsResponse;
import com.backend.dto.TopDefectLineDto;
import com.backend.service.StatsService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public MachineStatsResponse machineStats(
            @RequestParam String machineId,
            @RequestParam Instant start,
            @RequestParam Instant end
    ) {
        return statsService.machineStats(machineId, start, end);
    }

    @GetMapping("/top-defect-lines")
    public List<TopDefectLineDto> topDefectLines(
            @RequestParam String factoryId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return statsService.topDefectLines(factoryId, from, to, limit);
    }
}
