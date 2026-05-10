package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.List;

public record TestPreviewViewResponse(
        TestResponse test,
        List<TestQuestionViewResponse> questions,
        boolean preview,
        int totalPoints,
        Integer timeLimitMinutes,
        Instant generatedAt
) {
}
