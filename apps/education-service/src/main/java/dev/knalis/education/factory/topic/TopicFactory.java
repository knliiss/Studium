package dev.knalis.education.factory.topic;

import dev.knalis.education.entity.Topic;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TopicFactory {
    
    public Topic newTopic(UUID subjectId, String title, int orderIndex) {
        Topic topic = new Topic();
        topic.setSubjectId(subjectId);
        topic.setTitle(title.trim());
        topic.setOrderIndex(orderIndex);
        return topic;
    }
}
