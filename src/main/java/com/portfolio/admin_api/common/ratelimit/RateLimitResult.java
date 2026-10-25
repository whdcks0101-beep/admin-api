package com.portfolio.admin_api.common.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long remainingRequests,
        long retryAfterSeconds
) {
}
