package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class FileQuotaExceededException extends AppException {
    
    public FileQuotaExceededException(String quotaName, long currentValue, long limitValue) {
        super(
                HttpStatus.CONFLICT,
                "FILE_QUOTA_EXCEEDED",
                "File quota exceeded",
                Map.of(
                        "quotaName", quotaName,
                        "currentValue", currentValue,
                        "limitValue", limitValue
                )
        );
    }
}
