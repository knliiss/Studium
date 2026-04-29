package dev.knalis.schedule.factory.room;

import dev.knalis.schedule.entity.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomFactory {
    
    public Room newRoom(String code, String building, Integer floor, Integer capacity, boolean active) {
        Room room = new Room();
        room.setCode(code.trim());
        room.setBuilding(building.trim());
        room.setFloor(floor);
        room.setCapacity(capacity);
        room.setActive(active);
        return room;
    }
}
