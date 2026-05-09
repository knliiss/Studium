package dev.knalis.education.service.stream;

import dev.knalis.education.dto.request.CreateStreamRequest;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.entity.Stream;
import dev.knalis.education.exception.StreamHasGroupsException;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.StreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private GroupRepository groupRepository;

    private StreamService streamService;

    @BeforeEach
    void setUp() {
        streamService = new StreamService(streamRepository, specialtyRepository, groupRepository);
    }

    @Test
    void createStreamSavesActiveStream() {
        UUID specialtyId = UUID.randomUUID();
        Specialty specialty = new Specialty();
        specialty.setId(specialtyId);
        specialty.setActive(true);
        Stream stream = stream();
        when(specialtyRepository.findById(specialtyId)).thenReturn(Optional.of(specialty));
        when(streamRepository.save(any(Stream.class))).thenReturn(stream);

        assertEquals("S1", streamService.createStream(new CreateStreamRequest("S1", specialtyId, 1)).name());
    }

    @Test
    void archiveStreamRejectsAssignedGroups() {
        UUID streamId = UUID.randomUUID();
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(stream()));
        when(groupRepository.existsByStreamId(streamId)).thenReturn(true);
        assertThrows(StreamHasGroupsException.class, () -> streamService.archiveStream(streamId));
    }

    private Stream stream() {
        Stream stream = new Stream();
        stream.setId(UUID.randomUUID());
        stream.setName("S1");
        stream.setSpecialtyId(UUID.randomUUID());
        stream.setStudyYear(1);
        stream.setActive(true);
        stream.setCreatedAt(Instant.now());
        stream.setUpdatedAt(Instant.now());
        return stream;
    }
}
