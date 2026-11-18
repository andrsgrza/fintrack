package com.fintrack.app.service.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.BudgetNotification;
import com.fintrack.app.repository.BudgetNotificationRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Delivery/read-state boundary for BUDGET-6A's immutable materialized notification events. */
@Service
public class BudgetNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BudgetNotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BudgetNotificationService(
        BudgetNotificationRepository notificationRepository,
        CurrentUserService currentUserService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BudgetNotificationPage list(int pageNumber, int pageSize, boolean unreadOnly) {
        validatePage(pageNumber, pageSize);
        String login = currentUserService.getCurrentUserLogin();
        PageRequest page = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.desc("eventAt"), Sort.Order.desc("id")));
        Page<BudgetNotification> notifications = unreadOnly
            ? notificationRepository.findAllByUserLoginAndReadAtIsNullOrderByEventAtDescIdDesc(login, page)
            : notificationRepository.findAllByUserLoginOrderByEventAtDescIdDesc(login, page);
        return new BudgetNotificationPage(
            notifications.map(this::toView).getContent(),
            notifications.getNumber(),
            notifications.getSize(),
            notifications.getTotalElements(),
            notifications.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserLoginAndReadAtIsNull(currentUserService.getCurrentUserLogin());
    }

    @Transactional
    public BudgetNotificationView markRead(Long notificationId) {
        BudgetNotification notification = notificationRepository
            .findOneOwnedForReadMutation(notificationId, currentUserService.getCurrentUserLogin())
            // A foreign id is deliberately indistinguishable from a missing notification.
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND"));
        notification.markRead(Instant.now(clock));
        return toView(notification);
    }

    @Transactional
    public int markAllRead() {
        Instant cutoff = Instant.now(clock);
        return notificationRepository.markAllExistingUnreadAsRead(currentUserService.getCurrentUserLogin(), cutoff, cutoff);
    }

    private void validatePage(int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Notification pagination is invalid");
        }
    }

    private BudgetNotificationView toView(BudgetNotification notification) {
        try {
            return new BudgetNotificationView(
                notification.getId(),
                notification.getBudget().getId(),
                notification.getNotificationType(),
                objectMapper.readValue(notification.getSnapshotJson(), BudgetThresholdNotificationSnapshot.class),
                notification.getEventAt(),
                notification.getMaterializedAt(),
                notification.getReadAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Budget notification snapshot is invalid", exception);
        }
    }
}
