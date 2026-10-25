package com.portfolio.admin_api.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portfolio.admin_api.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long>{
	
	List<User> findByNameContaining(String name);
	
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
	
	boolean existsByEmailAndIdNot(String email, Long id);
}
