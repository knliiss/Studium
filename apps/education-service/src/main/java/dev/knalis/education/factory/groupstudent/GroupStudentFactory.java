package dev.knalis.education.factory.groupstudent;

import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.Subgroup;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupStudentFactory {
    
    public GroupStudent newGroupStudent(UUID groupId, UUID userId, GroupMemberRole role, Subgroup subgroup) {
        GroupStudent groupStudent = new GroupStudent();
        groupStudent.setGroupId(groupId);
        groupStudent.setUserId(userId);
        groupStudent.setRole(role == null ? GroupMemberRole.STUDENT : role);
        groupStudent.setSubgroup(subgroup == null ? Subgroup.ALL : subgroup);
        return groupStudent;
    }
}
