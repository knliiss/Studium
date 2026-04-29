package dev.knalis.education.factory.group;

import dev.knalis.education.entity.Group;
import org.springframework.stereotype.Component;

@Component
public class GroupFactory {
    
    public Group newGroup(String name) {
        Group group = new Group();
        group.setName(name.trim());
        return group;
    }
}
