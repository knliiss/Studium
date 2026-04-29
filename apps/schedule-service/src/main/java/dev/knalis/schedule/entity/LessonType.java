package dev.knalis.schedule.entity;

public enum LessonType {
    LECTURE("Lecture"),
    PRACTICAL("Practical"),
    LABORATORY("Laboratory");
    
    private final String displayName;
    
    LessonType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
