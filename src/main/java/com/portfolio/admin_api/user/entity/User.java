package com.portfolio.admin_api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "USERS") // DB에 만들어질 실제 테이블 이름
@Getter
@Setter
@NoArgsConstructor // 파라미터가 없는 기본 생성자를 자동으로 만들어줌 (JPA 필수)
public class User {
	
	// 2. Primary Key (PK) 지정 및 자동 증가 설정 (오라클의 시퀀스 역할)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
    // 3. 일반 컬럼들 세팅 (NOT NULL, 길이 제한 등)
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String role; // 예: "일반회원", "관리자"
	
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

}
