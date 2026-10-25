package com.portfolio.admin_api.user.dto;

import com.portfolio.admin_api.user.entity.User;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
