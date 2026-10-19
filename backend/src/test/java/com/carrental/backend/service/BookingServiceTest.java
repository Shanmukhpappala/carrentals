package com.carrental.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.carrental.backend.Dto.BookingRequestDto;
import com.carrental.backend.Dto.BookingResponseDto;
import com.carrental.backend.entity.Booking;
import com.carrental.backend.entity.Car;
import com.carrental.backend.entity.User;
import com.carrental.backend.Exceptions.CarNotAvailableException;
import com.carrental.backend.repositary.BookingRepositary;
import com.carrental.backend.repositary.CarRepositary;
import com.carrental.backend.repositary.UserRepositary;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepositary bookingRepository;

    @Mock
    private UserRepositary userRepository;

    @Mock
    private CarRepositary carRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, userRepository, carRepository);
    }

    @Test
    void createBooking_shouldChargeInclusiveDaysAtDailyRate() {
        User user = new User();
        user.setId(5L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setRole("CUSTOMER");

        Car car = new Car();
        car.setId(12L);
        car.setBrand("Toyota");
        car.setModel("Corolla");
        car.setStatus("AVAILABLE");
        car.setPricePerDay(new BigDecimal("120.00"));

        LocalDate pickup = LocalDate.now().plusDays(4);
        LocalDate returnDate = pickup.plusDays(2);

        BookingRequestDto request = new BookingRequestDto();
        request.setCarId(12L);
        request.setPickupDate(pickup);
        request.setReturnDate(returnDate);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(carRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsOverlappingBooking(eq(12L), eq(pickup), eq(returnDate))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(99L);
            return booking;
        });

        BookingResponseDto response = bookingService.createBooking(5L, request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("360.00"));
        assertThat(response.getNumberOfDays()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void bookingResponseKeepsStoredTotalAfterCarPriceChanges() {
        User user = new User();
        user.setId(5L);
        user.setName("Alice");
        Car car = new Car();
        car.setId(12L);
        car.setBrand("Toyota");
        car.setModel("Corolla");
        car.setStatus("AVAILABLE");
        car.setPricePerDay(new BigDecimal("120.00"));
        LocalDate pickup = LocalDate.now().plusDays(4);
        LocalDate returnDate = pickup.plusDays(2);
        Booking booking = new Booking(pickup, returnDate, new BigDecimal("360.00"), "CONFIRMED", LocalDate.now(), user, car);
        booking.setId(99L);
        car.setPricePerDay(new BigDecimal("200.00"));
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(booking));

        BookingResponseDto response = bookingService.getMyBooking(5L, 99L);

        assertThat(response.getTotalAmount()).isEqualByComparingTo("360.00");
        assertThat(response.getPricePerDay()).isEqualByComparingTo("200.00");
    }

    @Test
    void createBooking_shouldRejectSecondOverlappingRequest() {
        User user = new User();
        user.setId(5L);

        Car car = new Car();
        car.setId(12L);
        car.setStatus("AVAILABLE");

        LocalDate pickup = LocalDate.now().plusDays(4);
        LocalDate returnDate = pickup.plusDays(2);
        BookingRequestDto request = new BookingRequestDto();
        request.setCarId(12L);
        request.setPickupDate(pickup);
        request.setReturnDate(returnDate);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(carRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsOverlappingBooking(12L, pickup, returnDate)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(5L, request))
                .isInstanceOf(CarNotAvailableException.class)
                .hasMessageContaining("already booked");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void approveBooking_shouldRejectIfAnotherBlockingOverlapExists() {
        Booking booking = new Booking();
        booking.setId(77L);
        booking.setStatus("REQUESTED");
        booking.setPickupDate(LocalDate.now().plusDays(10));
        booking.setReturnDate(LocalDate.now().plusDays(12));

        User user = new User();
        user.setId(5L);
        user.setName("Alice");
        booking.setUser(user);

        Car car = new Car();
        car.setId(55L);
        car.setStatus("AVAILABLE");
        booking.setCar(car);

        when(carRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(car));
        when(bookingRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(booking));
        when(bookingRepository.existsOverlappingBookingExcluding(
                55L, booking.getPickupDate(), booking.getReturnDate(), 77L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.approveBooking(77L))
                .isInstanceOf(CarNotAvailableException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void approveBooking_shouldSucceedWhenNoOtherBlockingOverlapExists() {
        Booking booking = requestedBooking(77L, 55L);
        Car car = booking.getCar();

        when(bookingRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(booking));
        when(carRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsOverlappingBookingExcluding(
                55L, booking.getPickupDate(), booking.getReturnDate(), 77L)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponseDto response = bookingService.approveBooking(77L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        verify(bookingRepository).existsOverlappingBookingExcluding(
                55L, booking.getPickupDate(), booking.getReturnDate(), 77L);
    }

    @Test
    void pickupBooking_shouldRejectIfAnotherBlockingOverlapExists() {
        Booking booking = requestedBooking(77L, 55L);
        booking.setStatus("CONFIRMED");
        Car car = booking.getCar();

        when(bookingRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(booking));
        when(carRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsOverlappingBookingExcluding(
                55L, booking.getPickupDate(), booking.getReturnDate(), 77L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.pickupBooking(77L))
                .isInstanceOf(CarNotAvailableException.class);
    }

    private Booking requestedBooking(Long bookingId, Long carId) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus("REQUESTED");
        booking.setPickupDate(LocalDate.now().plusDays(10));
        booking.setReturnDate(LocalDate.now().plusDays(12));

        User user = new User();
        user.setId(5L);
        user.setName("Alice");
        booking.setUser(user);

        Car car = new Car();
        car.setId(carId);
        car.setStatus("AVAILABLE");
        car.setBrand("Toyota");
        car.setModel("Corolla");
        car.setPricePerDay(new BigDecimal("120.00"));
        booking.setCar(car);
        return booking;
    }
}
