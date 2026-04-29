package dev.knalis.education.dto.request;

import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.Subgroup;
import jakarta.validation.constraints.NotNull;

public record UpdateGroupStudentRequest(

        @NotNull
        GroupMemberRole role,

        @NotNull
        Subgroup subgroup
) {
}
