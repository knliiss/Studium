package dev.knalis.education.factory.lecture;

import dev.knalis.education.entity.Lecture;
import dev.knalis.education.entity.LectureStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LectureFactory {

    public Lecture newLecture(
            UUID subjectId,
            UUID topicId,
            String title,
            String content,
            int orderIndex,
            UUID createdByUserId
    ) {
        Lecture lecture = new Lecture();
        lecture.setSubjectId(subjectId);
        lecture.setTopicId(topicId);
        lecture.setTitle(title.trim());
        lecture.setContent(normalizeContent(content));
        lecture.setStatus(LectureStatus.DRAFT);
        lecture.setOrderIndex(orderIndex);
        lecture.setCreatedByUserId(createdByUserId);
        return lecture;
    }

    public void updateLecture(Lecture lecture, String title, String content, int orderIndex) {
        lecture.setTitle(title.trim());
        lecture.setContent(normalizeContent(content));
        lecture.setOrderIndex(orderIndex);
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim();
    }
}

