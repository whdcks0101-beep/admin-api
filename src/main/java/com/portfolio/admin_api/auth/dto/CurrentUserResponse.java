package com.portfolio.admin_api.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        String username,
        List<String> authorities
) {

	//간결한 생성자(compact constructor) 
	//record는 필드 재할당을 막지만, 
	//안에 들어 있는 리스트까지 자동으로 변경 불가능하게 만들지는 않는다. 
	//그래서 권한 목록을 수정할 수 없는 복사본으로 보관
    public CurrentUserResponse {
        authorities = List.copyOf(authorities);
    }
}