package dev.knalis.education.service.subject;

import dev.knalis.education.dto.request.CreateSubjectRequest;
import dev.knalis.education.dto.response.SubjectPageResponse;
import dev.knalis.education.dto.response.SubjectResponse;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.exception.GroupNotFoundException;
import dev.knalis.education.factory.subject.SubjectFactory;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectGroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.service.common.EducationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {
    
    @Mock
    private SubjectRepository subjectRepository;
    
    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private SubjectGroupRepository subjectGroupRepository;

    @Mock
    private SubjectTeacherRepository subjectTeacherRepository;

    @Mock
    private EducationAuditService educationAuditService;
    
    private SubjectService subjectService;
    
    @BeforeEach
    void setUp() {
        subjectService = new SubjectService(
                subjectRepository,
                groupRepository,
                subjectGroupRepository,
                subjectTeacherRepository,
                new SubjectFactory(),
                educationAuditService
        );
    }
    
    @Test
    void createSubjectThrowsWhenGroupIsMissing() {
        UUID groupId = UUID.randomUUID();
        
        when(groupRepository.existsById(groupId)).thenReturn(false);
        
        assertThrows(
                GroupNotFoundException.class,
                () -> subjectService.createSubject(UUID.randomUUID(), new CreateSubjectRequest(
                        "Math",
                        groupId,
                        null,
                        null,
                        "Core subject"
                ))
        );
    }
    
    @Test
    void getSubjectsByGroupReturnsPageResponse() {
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Subject subject = new Subject();
        subject.setId(subjectId);
        subject.setGroupId(groupId);
        subject.setName("Math");
        subject.setDescription("Core subject");
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);
        
        SubjectResponse subjectResponse = new SubjectResponse(
                subjectId,
                "Math",
                groupId,
                List.of(groupId),
                List.of(),
                "Core subject",
                now,
                now
        );
        
        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(subjectRepository.findAllByBoundGroupId(eq(groupId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subject)));
        when(subjectGroupRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());
        when(subjectTeacherRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId)).thenReturn(List.of());
        
        SubjectPageResponse result = subjectService.getSubjectsByGroup(groupId, 0, 20, null, null);
        
        assertEquals(List.of(subjectResponse), result.items());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.totalPages());
    }
}
