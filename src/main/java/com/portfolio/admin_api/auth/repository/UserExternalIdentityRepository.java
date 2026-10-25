package com.portfolio.admin_api.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.portfolio.admin_api.auth.entity.UserExternalIdentity;

public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, Long>{

    @EntityGraph(attributePaths = "user")
    Optional<UserExternalIdentity> findByIssuerAndSubject(
            String issuer,
            String subject
    );
	
}
