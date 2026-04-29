package dev.knalis.education.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReorderTopicsRequest(

        @Valid
        @NotNull
        @Size(min = 1)
        List<TopicOrderItem> topics
) {

    public record TopicOrderItem(

            @NotNull
            UUID topicId,

            @NotNull
            @PositiveOrZero
            Integer orderIndex
    ) {
    }
}
