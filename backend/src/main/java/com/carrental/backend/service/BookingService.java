package com.carrental.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.backend.Dto.BookingRequestDto;
import com.carrental.backend.Dto.BookingResponseDto;
import com.carrental.backend.Exceptions.BookingNotFoundException;
import com.carrental.backend.Exceptions.CarNotAvailableException;
import com.carrental.backend.Exceptions.CarNotFoundException;
import com.carrental.backend.Exceptions.InvalidBookingException;
import com.carrental.backend.Exceptions.UserNotFoundException;
import com.carrental.backend.entity.Booking;
import com.carrental.backend.entity.Car;
import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.BookingRepositary;
import com.carrental.backend.repositary.CarRepositary;
import com.carrental.backend.repositary.UserRepositary;

@Service
public class BookingService {

    private static final String REQUESTED = "REQUESTED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String REJECTED = "REJECTED";
    private static final String NO_SHOW = "NO_SHOW";
    private static final String AVAILABLE = "AVAILABLE";

    private final BookingRepositary bookingRepository;
    private final UserRepositary userRepository;
    private final CarRepositary carRepository;

    public BookingService(
            BookingRepositary bookingRepository,
            UserRepositary userRepository,
            CarRepositary carRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.carRepository = carRepository;
    }

    @Transactional
    public BookingResponseDto createBooking(Long userId, BookingRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        Car car = lockCarForUpdate(dto.getCarId());

        validateDates(dto.getPickupDate(), dto.getReturnDate());

        if (!AVAILABLE.equals(car.getStatus())) {
            throw new CarNotAvailableException("Car is currently not available");
        }

        if (bookingRepository.existsOverlappingBooking(car.getId(), dto.getPickupDate(), dto.getReturnDate())) {
            throw new CarNotAvailableException("Car is already booked for the selected dates");
        }

        long numberOfDays = calculateDays(dto.getPickupDate(), dto.getReturnDate());
        BigDecimal totalAmount = car.getPricePerDay().multiply(BigDecimal.valueOf(numberOfDays));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setCar(car);
        booking.setPickupDate(dto.getPickupDate());
        booking.setReturnDate(dto.getReturnDate());
        booking.setTotalAmount(totalAmount);
        booking.setStatus(REQUESTED);
        booking.setCreatedAt(LocalDate.now());

        return convertToResponse(bookingRepository.save(booking));
    }

    public List<BookingResponseDto> getMyBookings(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public BookingResponseDto getMyBooking(Long userId, Long bookingId) {
        Booking booking = findBookingOrThrow(bookingId);

        if (!booking.getUser().getId().equals(userId)) {
            throw new BookingNotFoundException("Booking does not belong to this user");
        }

        return convertToResponse(booking);
    }

    @Transactional
    public BookingResponseDto customerCancelBooking(Long userId, Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!booking.getUser().getId().equals(userId)) {
            throw new BookingNotFoundException("Booking does not belong to this user");
        }

        String status = booking.getStatus();
        if (!REQUESTED.equals(status) && !CONFIRMED.equals(status)) {
            throw new InvalidBookingException("Booking cannot be cancelled in status: " + status);
        }

        booking.setStatus(CANCELLED);
        return convertToResponse(bookingRepository.save(booking));
    }

    public List<BookingResponseDto> getAllBookings() {
        return bookingRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDto approveBooking(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!REQUESTED.equals(booking.getStatus())) {
            throw new InvalidBookingException("Only REQUESTED bookings can be approved");
        }

        Car car = lockCarForUpdate(booking.getCar().getId());
        booking.setCar(car);

        if (!AVAILABLE.equals(car.getStatus())) {
            throw new CarNotAvailableException("Car is currently not available for approval");
        }

        if (bookingRepository.existsOverlappingBookingExcluding(
                car.getId(), booking.getPickupDate(), booking.getReturnDate(), booking.getId())) {
            throw new CarNotAvailableException("Car is no longer available for these dates");
        }

        booking.setStatus(CONFIRMED);
        return convertToResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto rejectBooking(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!REQUESTED.equals(booking.getStatus())) {
            throw new InvalidBookingException("Only REQUESTED bookings can be rejected");
        }

        booking.setStatus(REJECTED);
        return convertToResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto adminCancelBooking(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        String status = booking.getStatus();
        if (!REQUESTED.equals(status) && !CONFIRMED.equals(status)) {
            throw new InvalidBookingException("Booking cannot be cancelled in status: " + status);
        }

        booking.setStatus(CANCELLED);
        return convertToResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto pickupBooking(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!CONFIRMED.equals(booking.getStatus())) {
            throw new InvalidBookingException("Only CONFIRMED bookings can be marked as ACTIVE");
        }

        Car car = lockCarForUpdate(booking.getCar().getId());
        booking.setCar(car);
        if (bookingRepository.existsOverlappingBookingExcluding(
                car.getId(), booking.getPickupDate(), booking.getReturnDate(), booking.getId())) {
            throw new CarNotAvailableException("Car is no longer available for these dates");
        }

        booking.setStatus(ACTIVE);
        return convertToResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto completeBooking(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!ACTIVE.equals(booking.getStatus())) {
            throw new InvalidBookingException("Only ACTIVE bookings can be completed");
        }

        booking.setStatus(COMPLETED);
        return convertToResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto markNoShow(Long bookingId) {
        Booking booking = findBookingForUpdateOrThrow(bookingId);

        if (!CONFIRMED.equals(booking.getStatus())) {
            throw new InvalidBookingException("Only CONFIRMED bookings can be marked as NO_SHOW");
        }

        booking.setStatus(NO_SHOW);
        return convertToResponse(bookingRepository.save(booking));
    }

    private void validateDates(LocalDate pickupDate, LocalDate returnDate) {
        if (pickupDate == null || returnDate == null) {
            throw new InvalidBookingException("Pickup date and return date are required");
        }

        if (pickupDate.isBefore(LocalDate.now())) {
            throw new InvalidBookingException("Pickup date cannot be in the past");
        }

        if (returnDate.isBefore(pickupDate)) {
            throw new InvalidBookingException("Return date cannot be before pickup date");
        }
    }

    private long calculateDays(LocalDate pickupDate, LocalDate returnDate) {
        return ChronoUnit.DAYS.between(pickupDate, returnDate) + 1;
    }

    private Booking findBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));
    }

    private Booking findBookingForUpdateOrThrow(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));
    }

    private Car lockCarForUpdate(Long carId) {
        return carRepository.findByIdForUpdate(carId)
                .orElseThrow(() -> new CarNotFoundException("Car not found with id: " + carId));
    }

    private BookingResponseDto convertToResponse(Booking booking) {
        BookingResponseDto dto = new BookingResponseDto();
        dto.setId(booking.getId());
        dto.setUserId(booking.getUser().getId());
        dto.setUserName(booking.getUser().getName());
        dto.setCarId(booking.getCar().getId());
        dto.setCarBrand(booking.getCar().getBrand());
        dto.setCarModel(booking.getCar().getModel());
        dto.setPickupDate(booking.getPickupDate());
        dto.setReturnDate(booking.getReturnDate());
        dto.setNumberOfDays(Math.toIntExact(calculateDays(booking.getPickupDate(), booking.getReturnDate())));
        dto.setPricePerDay(booking.getCar().getPricePerDay());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setStatus(booking.getStatus());
        dto.setCreatedAt(booking.getCreatedAt());
        return dto;
    }
}
