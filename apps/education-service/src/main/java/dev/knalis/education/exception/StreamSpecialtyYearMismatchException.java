package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class StreamSpecialtyYearMismatchException extends AppException {

    public StreamSpecialtyYearMismatchException(UUID streamId, UUID specialtyId, Integer studyYear) {
        super(
                HttpStatus.CONFLICT,
                "STREAM_SPECIALTY_YEAR_MISMATCH",
                "Stream specialty or study year does not match group",
                Map.of(
                        "streamId", streamId,
                        "specialtyId", specialtyId,
                        "studyYear", studyYear
                )
        );
    }
}
