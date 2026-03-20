package com.wsi.platform.websocket;

import java.time.LocalDateTime;

/**
 * WebSocket notification pushed to clients.
 *
 * type:
 *   REVIEW_PENDING  — broadcast to /topic/review-notification  (reviewers subscribe)
 *   APPROVED        — point-to-point to annotator /queue/notifications
 *   REJECTED        — point-to-point to annotator /queue/notifications
 */
public record NotificationPayload(
        String type,
        Long slideId,
        String slideName,
        String message,
        LocalDateTime timestamp
) {}
