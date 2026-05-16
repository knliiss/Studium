package dev.knalis.notification.controller;

import dev.knalis.notification.dto.response.NotificationPageResponse;
import dev.knalis.notification.dto.response.NotificationResponse;
import dev.knalis.notification.dto.response.UnreadCountResponse;
import dev.knalis.notification.entity.NotificationStatus;
import dev.knalis.notification.service.NotificationService;
import dev.knalis.shared.security.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;
    
    @GetMapping
    public NotificationPageResponse getNotifications(
            Authentication authentication,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return notificationService.getMyNotifications(
                currentUserService.getCurrentUserId(authentication),
                status,
                page,
                size,
                sortBy,
                direction
        );
    }
    
    @GetMapping("/me")
    public NotificationPageResponse getMyNotifications(
            Authentication authentication,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return getNotifications(authentication, status, page, size, sortBy, direction);
    }
    
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCountRoot(Authentication authentication) {
        return notificationService.getUnreadCount(currentUserService.getCurrentUserId(authentication));
    }
    
    @GetMapping("/me/unread-count")
    public UnreadCountResponse getUnreadCount(Authentication authentication) {
        return getUnreadCountRoot(authentication);
    }
    
    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            Authentication authentication,
            @PathVariable UUID notificationId
    ) {
        return notificationService.markAsRead(
                currentUserService.getCurrentUserId(authentication),
                notificationId
        );
    }
    
    @PatchMapping("/me/read-all")
    public UnreadCountResponse markAllAsRead(Authentication authentication) {
        return markAllAsReadRoot(authentication);
    }
    
    @PatchMapping("/read-all")
    public UnreadCountResponse markAllAsReadRoot(Authentication authentication) {
        return notificationService.markAllAsRead(currentUserService.getCurrentUserId(authentication));
    }
    
    @DeleteMapping("/{notificationId}")
    public void delete(
            Authentication authentication,
            @PathVariable UUID notificationId
    ) {
        notificationService.delete(
                currentUserService.getCurrentUserId(authentication),
                notificationId
        );
    }

    @DeleteMapping("/me")
    public UnreadCountResponse deleteAllMyNotifications(Authentication authentication) {
        return deleteAllNotifications(authentication);
    }

    @DeleteMapping
    public UnreadCountResponse deleteAllNotifications(Authentication authentication) {
        return notificationService.deleteAll(currentUserService.getCurrentUserId(authentication));
    }
}
