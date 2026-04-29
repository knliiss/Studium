package dev.knalis.education.service.search;

import dev.knalis.education.dto.response.SearchItemResponse;
import dev.knalis.education.dto.response.SearchPageResponse;
import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.Subject;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EducationSearchService {

    private final GroupRepository groupRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public SearchPageResponse search(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(0, Math.min(Math.max((Math.max(page, 0) + 1) * Math.max(size, 1), 1), 100));
        String normalizedQuery = query == null ? "" : query.trim();
        Page<Group> groups = groupRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery, pageRequest);
        Page<Subject> subjects = subjectRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery, pageRequest);
        Page<Topic> topics = topicRepository.findAllByTitleContainingIgnoreCaseOrderByTitleAsc(normalizedQuery, pageRequest);

        List<SearchItemResponse> merged = Stream.concat(
                        Stream.concat(
                                groups.getContent().stream().map(this::toGroupItem),
                                subjects.getContent().stream().map(this::toSubjectItem)
                        ),
                        topics.getContent().stream().map(this::toTopicItem)
                )
                .sorted(Comparator
                        .comparing(SearchItemResponse::title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.id().toString()))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int fromIndex = Math.min(safePage * safeSize, merged.size());
        int toIndex = Math.min(fromIndex + safeSize, merged.size());
        long totalElements = groups.getTotalElements() + subjects.getTotalElements() + topics.getTotalElements();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        return new SearchPageResponse(
                merged.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safePage == 0,
                totalPages == 0 || safePage >= totalPages - 1
        );
    }

    private SearchItemResponse toGroupItem(Group group) {
        return new SearchItemResponse(
                "GROUP",
                group.getId(),
                group.getName(),
                "Group",
                Map.of("groupId", group.getId())
        );
    }

    private SearchItemResponse toSubjectItem(Subject subject) {
        return new SearchItemResponse(
                "SUBJECT",
                subject.getId(),
                subject.getName(),
                "Group " + subject.getGroupId(),
                Map.of(
                        "subjectId", subject.getId(),
                        "groupId", subject.getGroupId()
                )
        );
    }

    private SearchItemResponse toTopicItem(Topic topic) {
        return new SearchItemResponse(
                "TOPIC",
                topic.getId(),
                topic.getTitle(),
                "Subject " + topic.getSubjectId(),
                Map.of(
                        "topicId", topic.getId(),
                        "subjectId", topic.getSubjectId()
                )
        );
    }
}
