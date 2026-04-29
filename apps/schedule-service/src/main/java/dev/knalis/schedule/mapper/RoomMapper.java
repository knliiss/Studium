package dev.knalis.schedule.mapper;

import dev.knalis.schedule.dto.response.RoomResponse;
import dev.knalis.schedule.entity.Room;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoomMapper {
    
    RoomResponse toResponse(Room room);
}
