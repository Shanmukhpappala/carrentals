package com.carrental.backend.repositary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.carrental.backend.entity.Car;

public interface CarRepositary extends JpaRepository<Car, Long>{
	 List<Car> findByStatus(String status);

	 @Query("SELECT COUNT(c) > 0 FROM Car c WHERE upper(trim(c.registrationNumber)) = upper(trim(:registrationNumber)) AND (:carIdToIgnore IS NULL OR c.id <> :carIdToIgnore)")
	 boolean existsByNormalizedRegistrationNumber(
			@Param("registrationNumber") String registrationNumber,
			@Param("carIdToIgnore") Long carIdToIgnore);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Car c WHERE c.id = :carId")
	Optional<Car> findByIdForUpdate(@Param("carId") Long carId);
}
