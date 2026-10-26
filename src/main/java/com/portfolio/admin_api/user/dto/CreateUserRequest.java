package com.portfolio.admin_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "회원 구분은 필수입니다.")
        @Size(max = 20, message = "회원 구분은 20자 이하여야 합니다.")
        @Pattern(
                regexp = "^(일반회원|우수회원|관리자|체험관리자)$",
                message = "회원 구분은 일반회원, 우수회원, 관리자, 체험관리자 중 하나여야 합니다."
        )        
        String role,
        
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 12, max = 64, message = "비밀번호는 12~64자여야 합니다.")
        String password
        
) {
	@Override
	public String toString() {
	    return "CreateUserRequest[REDACTED]";
	}
}
