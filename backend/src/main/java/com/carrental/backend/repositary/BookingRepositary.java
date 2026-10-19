package com.carrental.backend.repositary;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import com.carrental.backend.entity.Booking;

public interface BookingRepositary extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByCarId(Long carId);

    List<Booking> findByStatus(String status);

    List<Booking> findByUserIdAndStatus(
            Long userId,
            String status
    );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
        java.util.Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.car.id = :carId
        AND b.status IN ('REQUESTED', 'CONFIRMED', 'ACTIVE')
        AND b.pickupDate <= :returnDate
        AND b.returnDate >= :pickupDate
    """)
    boolean existsOverlappingBooking(
            @Param("carId") Long carId,
            @Param("pickupDate") LocalDate pickupDate,
            @Param("returnDate") LocalDate returnDate
    );

    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.car.id = :carId
        AND b.id <> :excludedBookingId
        AND b.status IN ('REQUESTED', 'CONFIRMED', 'ACTIVE')
        AND b.pickupDate <= :returnDate
        AND b.returnDate >= :pickupDate
    """)
    boolean existsOverlappingBookingExcluding(
            @Param("carId") Long carId,
            @Param("pickupDate") LocalDate pickupDate,
            @Param("returnDate") LocalDate returnDate,
            @Param("excludedBookingId") Long excludedBookingId
    );

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.car.id = :carId AND b.status IN ('CONFIRMED', 'ACTIVE')")
    boolean existsConfirmedOrActiveBookingForCar(@Param("carId") Long carId);
}
