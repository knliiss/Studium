package dev.knalis.education.factory.group;

import dev.knalis.education.entity.Group;
import dev.knalis.education.entity.GroupSubgroupMode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupFactory {
    
    public Group newGroup(
            String name,
            UUID specialtyId,
            Integer studyYear,
            UUID streamId,
            GroupSubgroupMode subgroupMode
    ) {
        Group group = new Group();
        group.setName(name.trim());
        group.setSpecialtyId(specialtyId);
        group.setStudyYear(studyYear);
        group.setStreamId(streamId);
        group.setSubgroupMode(subgroupMode == null ? GroupSubgroupMode.NONE : subgroupMode);
        return group;
    }
}
