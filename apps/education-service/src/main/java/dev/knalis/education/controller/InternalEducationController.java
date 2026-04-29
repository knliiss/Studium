package dev.knalis.education.controller;

import dev.knalis.education.dto.response.GroupStudentUserResponse;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.dto.response.GroupMembershipResponse;
import dev.knalis.education.dto.response.SubjectResponse;
import dev.knalis.education.dto.response.TopicResponse;
import dev.knalis.education.service.InternalRequestGuard;
import dev.knalis.education.service.group.GroupService;
import dev.knalis.education.service.subject.SubjectService;
import dev.knalis.education.service.topic.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/education")
@RequiredArgsConstructor
public class InternalEducationController {
    
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    
    private final GroupService groupService;
    private final TopicService topicService;
    private final SubjectService subjectService;
    private final InternalRequestGuard internalRequestGuard;
    
    @GetMapping("/groups/{groupId}/students")
    public List<GroupStudentUserResponse> getGroupStudents(
            @PathVariable UUID groupId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return groupService.getStudentUsersByGroup(groupId);
    }

    @GetMapping("/groups/{groupId}")
    public GroupResponse getGroup(
            @PathVariable UUID groupId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return groupService.getGroup(groupId);
    }

    @GetMapping("/users/{userId}/groups")
    public List<GroupMembershipResponse> getGroupsByUser(
            @PathVariable UUID userId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return groupService.getGroupsByUser(userId);
    }
    
    @GetMapping("/topics/{topicId}")
    public TopicResponse getTopic(
            @PathVariable UUID topicId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return topicService.getTopic(topicId);
    }
    
    @GetMapping("/subjects/{subjectId}")
    public SubjectResponse getSubject(
            @PathVariable UUID subjectId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return subjectService.getSubject(subjectId);
    }

    @GetMapping("/groups/{groupId}/subjects")
    public List<SubjectResponse> getSubjectsByGroup(
            @PathVariable UUID groupId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return subjectService.getSubjectsByGroup(groupId);
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public List<TopicResponse> getTopicsBySubject(
            @PathVariable UUID subjectId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return topicService.getTopicsBySubject(subjectId);
    }
}
