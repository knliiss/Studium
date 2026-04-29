package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.CreateLessonSlotRequest;
import dev.knalis.schedule.dto.request.UpdateLessonSlotRequest;
import dev.knalis.schedule.dto.response.LessonSlotResponse;
import dev.knalis.schedule.service.slot.LessonSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule/slots")
@RequiredArgsConstructor
public class LessonSlotController {
    
    private final LessonSlotService lessonSlotService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public LessonSlotResponse createLessonSlot(@Valid @RequestBody CreateLessonSlotRequest request) {
        return lessonSlotService.createLessonSlot(request);
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<LessonSlotResponse> getLessonSlots() {
        return lessonSlotService.getLessonSlots();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public LessonSlotResponse updateLessonSlot(
            @PathVariable("id") UUID slotId,
            @Valid @RequestBody UpdateLessonSlotRequest request
    ) {
        return lessonSlotService.updateLessonSlot(slotId, request);
    }
}
