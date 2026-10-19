package com.carrental.backend.repositary;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.carrental.backend.entity.User;
public interface UserRepositary extends JpaRepository<User, Long>{
	 @Query("SELECT u FROM User u WHERE lower(trim(u.email)) = lower(trim(:email))")
	 Optional<User> findByEmail(@Param("email") String email);

	 @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(trim(u.email)) = lower(trim(:email))")
	 boolean existsByEmail(@Param("email") String email);
}
