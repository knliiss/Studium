package dev.knalis.file.mapper;

import dev.knalis.file.dto.response.StoredFileResponse;
import dev.knalis.file.entity.StoredFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Locale;

@Mapper(componentModel = "spring")
public interface StoredFileMapper {
    
    @Mapping(target = "fileId", source = "id")
    @Mapping(target = "visibility", source = "access")
    @Mapping(target = "previewAvailable", expression = "java(isPreviewAvailable(storedFile.getContentType()))")
    StoredFileResponse toResponse(StoredFile storedFile);
    
    default boolean isPreviewAvailable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf") || normalized.startsWith("image/");
    }
}
