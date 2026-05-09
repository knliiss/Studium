package dev.knalis.education.service.stream;

import dev.knalis.education.dto.request.CreateStreamRequest;
import dev.knalis.education.dto.request.UpdateStreamRequest;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.dto.response.StreamResponse;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.Stream;
import dev.knalis.education.exception.SpecialtyNotActiveException;
import dev.knalis.education.exception.SpecialtyNotFoundException;
import dev.knalis.education.exception.StreamHasGroupsException;
import dev.knalis.education.exception.StreamNotFoundException;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamRepository streamRepository;
    private final SpecialtyRepository specialtyRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public StreamResponse createStream(CreateStreamRequest request) {
        ensureSpecialtyActive(request.specialtyId());
        Stream stream = new Stream();
        stream.setName(request.name().trim());
        stream.setSpecialtyId(request.specialtyId());
        stream.setStudyYear(request.studyYear());
        stream.setActive(true);
        return toResponse(streamRepository.save(stream));
    }

    @Transactional
    public StreamResponse updateStream(UUID streamId, UpdateStreamRequest request) {
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));
        ensureSpecialtyActive(request.specialtyId());
        stream.setName(request.name().trim());
        stream.setSpecialtyId(request.specialtyId());
        stream.setStudyYear(request.studyYear());
        return toResponse(streamRepository.save(stream));
    }

    @Transactional(readOnly = true)
    public StreamResponse getStream(UUID streamId) {
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));
        return toResponse(stream);
    }

    @Transactional(readOnly = true)
    public List<StreamResponse> listStreams(UUID specialtyId, Integer studyYear, Boolean active) {
        List<Stream> streams = streamRepository.findAllByOrderByNameAsc();
        return streams.stream()
                .filter(stream -> specialtyId == null || specialtyId.equals(stream.getSpecialtyId()))
                .filter(stream -> studyYear == null || studyYear.equals(stream.getStudyYear()))
                .filter(stream -> active == null || active.equals(stream.isActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StreamResponse archiveStream(UUID streamId) {
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));
        if (groupRepository.existsByStreamId(streamId)) {
            throw new StreamHasGroupsException(streamId);
        }
        stream.setActive(false);
        return toResponse(streamRepository.save(stream));
    }

    @Transactional
    public StreamResponse restoreStream(UUID streamId) {
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));
        ensureSpecialtyActive(stream.getSpecialtyId());
        stream.setActive(true);
        return toResponse(streamRepository.save(stream));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsByStream(UUID streamId) {
        if (!streamRepository.existsById(streamId)) {
            throw new StreamNotFoundException(streamId);
        }
        return groupRepository.findAllByStreamIdOrderByNameAsc(streamId).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    private GroupResponse toGroupResponse(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getSpecialtyId(),
                group.getStudyYear(),
                group.getStreamId(),
                group.getSubgroupMode(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    private StreamResponse toResponse(Stream stream) {
        return new StreamResponse(
                stream.getId(),
                stream.getName(),
                stream.getSpecialtyId(),
                stream.getStudyYear(),
                stream.isActive(),
                stream.getCreatedAt(),
                stream.getUpdatedAt()
        );
    }

    private void ensureSpecialtyActive(UUID specialtyId) {
        specialtyRepository.findById(specialtyId)
                .ifPresentOrElse(
                        specialty -> {
                            if (!specialty.isActive()) {
                                throw new SpecialtyNotActiveException(specialtyId);
                            }
                        },
                        () -> {
                            throw new SpecialtyNotFoundException(specialtyId);
                        }
                );
    }
}
