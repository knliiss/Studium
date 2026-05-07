package dev.knalis.education.mapper;

import dev.knalis.education.dto.response.LectureResponse;
import dev.knalis.education.entity.Lecture;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LectureMapper {

    LectureResponse toResponse(Lecture lecture);
}

