package com.backend.controller;

import com.backend.dto.BatchIngestResponse;
import com.backend.dto.EventIngestRequest;
import com.backend.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    @PostMapping("/batch")
    public BatchIngestResponse ingestBatch(@RequestBody List<@Valid EventIngestRequest> events) {
        return eventService.ingestBatch(events);
    }

    @GetMapping("/hello")
    public String hello(){
        return "Hello pavan";
    }
}
