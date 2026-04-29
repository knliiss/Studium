package dev.knalis.education.service.dashboard;

import dev.knalis.education.dto.response.EducationAdminOverviewResponse;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EducationDashboardService {

    private final GroupRepository groupRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public EducationAdminOverviewResponse getAdminOverview() {
        return new EducationAdminOverviewResponse(
                groupRepository.count(),
                subjectRepository.count(),
                topicRepository.count()
        );
    }
}
