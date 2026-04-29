package dev.knalis.contracts.event;

public enum ScheduleLessonTypeV1 {
    LECTURE("Lecture"),
    PRACTICAL("Practical"),
    LABORATORY("Laboratory");
    
    private final String displayName;
    
    ScheduleLessonTypeV1(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
