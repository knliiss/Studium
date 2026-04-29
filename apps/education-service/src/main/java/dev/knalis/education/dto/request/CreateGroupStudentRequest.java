package dev.knalis.education.dto.request;

import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.Subgroup;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGroupStudentRequest(

        @NotNull
        UUID userId,

        GroupMemberRole role,

        Subgroup subgroup
) {
}
