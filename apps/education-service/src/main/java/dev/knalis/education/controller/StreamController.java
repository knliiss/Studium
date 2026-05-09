package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateStreamRequest;
import dev.knalis.education.dto.request.UpdateStreamRequest;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.dto.response.StreamResponse;
import dev.knalis.education.service.stream.StreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/education/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<StreamResponse> listStreams(
            @RequestParam(required = false) UUID specialtyId,
            @RequestParam(required = false) Integer studyYear,
            @RequestParam(required = false) Boolean active
    ) {
        return streamService.listStreams(specialtyId, studyYear, active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public StreamResponse getStream(@PathVariable("id") UUID streamId) {
        return streamService.getStream(streamId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public StreamResponse createStream(@Valid @RequestBody CreateStreamRequest request) {
        return streamService.createStream(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public StreamResponse updateStream(@PathVariable("id") UUID streamId, @Valid @RequestBody UpdateStreamRequest request) {
        return streamService.updateStream(streamId, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public StreamResponse archiveStream(@PathVariable("id") UUID streamId) {
        return streamService.archiveStream(streamId);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public StreamResponse restoreStream(@PathVariable("id") UUID streamId) {
        return streamService.restoreStream(streamId);
    }

    @GetMapping("/{id}/groups")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<GroupResponse> getGroupsByStream(@PathVariable("id") UUID streamId) {
        return streamService.getGroupsByStream(streamId);
    }
}
