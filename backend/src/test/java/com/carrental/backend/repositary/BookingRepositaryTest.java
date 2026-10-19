package com.carrental.backend.repositary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carrental.backend.TestIntegrationProperties;
import com.carrental.backend.config.TestDatabaseIsolationValidator;
import com.carrental.backend.entity.Booking;
import com.carrental.backend.entity.Car;
import com.carrental.backend.entity.User;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseIsolationValidator.class)
@Transactional
class BookingRepositaryTest {

    @DynamicPropertySource
    static void testOnlySecurityProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> TestIntegrationProperties.JWT_SECRET);
    }

    @Autowired
    private BookingRepositary bookingRepository;

    @Autowired
    private UserRepositary userRepository;

    @Autowired
    private CarRepositary carRepository;

    @Test
    void existsOverlappingBooking_shouldUseInclusiveRangesAndIgnoreCancelledOrRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setName("Overlap User");
        user.setEmail("overlap-" + suffix + "@example.com");
        user.setPassword("encoded-password");
        user.setPhone("5550000000");
        user.setRole("CUSTOMER");
        user = userRepository.save(user);

        Car car = new Car();
        car.setBrand("Honda");
        car.setModel("Civic");
        car.setRegistrationNumber("REQ-" + suffix);
        car.setYear(2024);
        car.setFuelType("PETROL");
        car.setTransmission("AUTOMATIC");
        car.setSeatingCapacity(5);
        car.setPricePerDay(new BigDecimal("120.00"));
        car.setLocation("Bangalore");
        car.setStatus("AVAILABLE");
        car.setDescription("Test car");
        car = carRepository.save(car);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setCar(car);
        booking.setPickupDate(LocalDate.now().plusDays(15));
        booking.setReturnDate(LocalDate.now().plusDays(18));
        booking.setTotalAmount(new BigDecimal("360.00"));
        booking.setStatus("REQUESTED");
        booking.setCreatedAt(LocalDate.now());
        bookingRepository.save(booking);

        boolean overlap = bookingRepository.existsOverlappingBooking(
                car.getId(),
                LocalDate.now().plusDays(16),
                LocalDate.now().plusDays(19)
        );

        assertThat(overlap).isTrue();
        assertThat(bookingRepository.existsOverlappingBooking(
            car.getId(), booking.getReturnDate(), booking.getReturnDate().plusDays(2))).isTrue();
        assertThat(bookingRepository.existsOverlappingBooking(
            car.getId(), booking.getReturnDate().plusDays(1), booking.getReturnDate().plusDays(2))).isFalse();
        assertThat(bookingRepository.existsOverlappingBookingExcluding(
            car.getId(), booking.getPickupDate(), booking.getReturnDate(), booking.getId())).isFalse();

        for (String blockingStatus : List.of("REQUESTED", "CONFIRMED", "ACTIVE")) {
            booking.setStatus(blockingStatus);
            bookingRepository.saveAndFlush(booking);
            assertThat(bookingRepository.existsOverlappingBooking(
                car.getId(), booking.getReturnDate(), booking.getReturnDate().plusDays(2)))
                .as("overlap with %s booking", blockingStatus)
                .isTrue();
        }

        for (String nonBlockingStatus : List.of("COMPLETED", "CANCELLED", "REJECTED", "NO_SHOW")) {
            booking.setStatus(nonBlockingStatus);
            bookingRepository.saveAndFlush(booking);
            assertThat(bookingRepository.existsOverlappingBooking(
                car.getId(), booking.getPickupDate(), booking.getReturnDate()))
                .as("overlap with %s booking", nonBlockingStatus)
                .isFalse();
        }
    }
}
