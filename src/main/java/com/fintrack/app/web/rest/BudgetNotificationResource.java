package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetNotificationPage;
import com.fintrack.app.service.budget.BudgetNotificationService;
import com.fintrack.app.service.budget.BudgetNotificationView;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped, read-only delivery API for BUDGET notifications. */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class BudgetNotificationResource {

    private final BudgetNotificationService notificationService;

    public BudgetNotificationResource(BudgetNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<BudgetNotificationPage> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_PAGINATION");
        }
        return ResponseEntity.ok(notificationService.list(page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.unreadCount()));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<BudgetNotificationView> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(notificationId));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("markedCount", notificationService.markAllRead()));
    }
}
