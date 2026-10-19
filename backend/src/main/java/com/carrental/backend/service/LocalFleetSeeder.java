package com.carrental.backend.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.carrental.backend.Dto.CarRequestDto;
import com.carrental.backend.Dto.CarResponseDto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

/** Explicitly opt-in, local-only sample fleet loader. */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.local-fleet-seed", name = "enabled", havingValue = "true")
public class LocalFleetSeeder implements ApplicationRunner {

    private static final int TARGET_COUNT = 25;
    private static final String EXPECTED_JDBC_URL = "jdbc:postgresql://localhost:5432/carrentalsbackend";

    private final DataSource dataSource;
    private final CarService carService;
    private final Validator validator;
    private final boolean dryRun;

    public LocalFleetSeeder(DataSource dataSource, CarService carService, Validator validator,
            @org.springframework.beans.factory.annotation.Value("${app.local-fleet-seed.dry-run:true}") boolean dryRun) {
        this.dataSource = dataSource;
        this.carService = carService;
        this.validator = validator;
        this.dryRun = dryRun;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        verifyLocalDatabase();

        List<CarResponseDto> existing = carService.getAllCarsForAdmin();
        Set<String> registrationNumbers = uniqueRegistrationNumbers(existing);
        if (registrationNumbers.size() != existing.size()) {
            throw new IllegalStateException("Local fleet seed refused: existing vehicle registration numbers are not unique.");
        }

        List<CarRequestDto> missing = fleet().stream()
                .filter(car -> !registrationNumbers.contains(car.getRegistrationNumber().trim().toUpperCase(Locale.ROOT)))
                .toList();
        int slots = Math.max(0, TARGET_COUNT - existing.size());
        int planned = Math.min(slots, missing.size());

        if (dryRun) {
            System.out.printf("Local fleet seed dry run: database vehicle count=%d; planned inserts=%d; target=%d.%n",
                    existing.size(), planned, TARGET_COUNT);
            return;
        }

        int inserted = 0;
        for (CarRequestDto candidate : missing) {
            if (inserted >= slots) {
                break;
            }
            Set<ConstraintViolation<CarRequestDto>> violations = validator.validate(candidate);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            carService.addCar(candidate);
            inserted++;
        }
        List<CarResponseDto> finalInventory = carService.getAllCarsForAdmin();
        Set<String> finalRegistrationNumbers = uniqueRegistrationNumbers(finalInventory);
        if (finalRegistrationNumbers.size() != finalInventory.size()) {
            throw new IllegalStateException("Local fleet seed verification failed: vehicle registration numbers are not unique.");
        }
        System.out.printf("Local fleet seed complete: initial count=%d; inserted=%d; final count=%d; unique registrations=%d.%n",
                existing.size(), inserted, finalInventory.size(), finalRegistrationNumbers.size());
    }

    private static Set<String> uniqueRegistrationNumbers(List<CarResponseDto> cars) {
        Set<String> registrationNumbers = new HashSet<>();
        cars.stream().map(CarResponseDto::getRegistrationNumber)
                .filter(value -> value != null)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .forEach(registrationNumbers::add);
        return registrationNumbers;
    }

    private void verifyLocalDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String url = metadata.getURL();
            String catalog = connection.getCatalog();
            if (!"PostgreSQL".equalsIgnoreCase(metadata.getDatabaseProductName())
                    || !EXPECTED_JDBC_URL.equals(url)
                    || !"carrentalsbackend".equals(catalog)) {
                throw new IllegalStateException("Local fleet seed refused: datasource is not the exact approved local database.");
            }
        }
    }

    private static List<CarRequestDto> fleet() {
        List<CarRequestDto> cars = new ArrayList<>();
        cars.add(car("Maruti Suzuki", "Swift", "KA03-MLP-001", 2024, "Petrol", "Manual", 5, 1800, "Bengaluru", "Hatchback", "Compact hatchback suited to city trips.", "https://images.unsplash.com/photo-1503376780353-7e6692767b70"));
        cars.add(car("Hyundai", "i20", "KA03-MLP-002", 2023, "Petrol", "Automatic", 5, 2300, "Bengaluru", "Hatchback", "Premium hatchback with a comfortable cabin.", "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7"));
        cars.add(car("Tata", "Nexon", "KA03-MLP-003", 2024, "Petrol", "Automatic", 5, 3200, "Bengaluru", "Compact SUV", "Compact SUV for city and highway journeys.", "https://images.unsplash.com/photo-1519641471654-76ce0107ad1b"));
        cars.add(car("Hyundai", "Creta", "KA03-MLP-004", 2023, "Diesel", "Automatic", 5, 3800, "Bengaluru", "SUV", "Mid-size SUV with a spacious five-seat layout.", "https://images.unsplash.com/photo-1511919884226-fd3cad34687c"));
        cars.add(car("Kia", "Seltos", "KA03-MLP-005", 2024, "Petrol", "Automatic", 5, 3900, "Bengaluru", "SUV", "Feature-rich mid-size SUV for longer drives.", "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf"));
        cars.add(car("Honda", "City", "KA03-MLP-006", 2023, "Petrol", "Automatic", 5, 3400, "Bengaluru", "Sedan", "Comfortable sedan with generous passenger space.", "https://images.unsplash.com/photo-1552519507-da3b142c6e3d"));
        cars.add(car("Maruti Suzuki", "Ertiga", "KA03-MLP-007", 2024, "Petrol", "Manual", 7, 3100, "Bengaluru", "MPV", "Seven-seat MPV for family and group travel.", "https://images.unsplash.com/photo-1549317661-bd32c8ce0db2"));
        cars.add(car("Toyota", "Innova Crysta", "KA03-MLP-008", 2022, "Diesel", "Manual", 7, 5000, "Bengaluru", "MPV", "Spacious seven-seat MPV for extended journeys.", "https://images.unsplash.com/photo-1563720223185-11003d516935"));
        cars.add(car("Tata", "Punch", "KA03-MLP-009", 2024, "Petrol", "Manual", 5, 2500, "Bengaluru", "Micro SUV", "Compact high-riding car suited to urban use.", "https://images.unsplash.com/photo-1502161254066-6c74afbf07aa"));
        cars.add(car("Mahindra", "Thar", "KA03-MLP-010", 2023, "Diesel", "Manual", 4, 5200, "Bengaluru", "Off-road SUV", "Four-seat lifestyle SUV for planned road trips.", "https://images.unsplash.com/photo-1519608487953-e999c86e7455"));
        cars.add(car("Hyundai", "Venue", "KA03-MLP-011", 2023, "Petrol", "Manual", 5, 2800, "Bengaluru", "Compact SUV", "Compact SUV with a practical five-seat cabin.", "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf"));
        cars.add(car("Kia", "Sonet", "KA03-MLP-012", 2024, "Diesel", "Manual", 5, 3000, "Bengaluru", "Compact SUV", "Compact SUV suited to city commutes and weekends.", "https://images.unsplash.com/photo-1519641471654-76ce0107ad1b"));
        cars.add(car("Skoda", "Slavia", "KA03-MLP-013", 2023, "Petrol", "Automatic", 5, 3600, "Bengaluru", "Sedan", "Mid-size sedan for comfortable highway travel.", "https://images.unsplash.com/photo-1552519507-da3b142c6e3d"));
        cars.add(car("Tata", "Altroz", "KA03-MLP-014", 2022, "Petrol", "Manual", 5, 2100, "Bengaluru", "Hatchback", "Premium hatchback with a practical cabin.", "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7"));
        cars.add(car("Toyota", "Glanza", "KA03-MLP-015", 2024, "Petrol", "Manual", 5, 2000, "Bengaluru", "Hatchback", "Efficient hatchback for everyday city driving.", "https://images.unsplash.com/photo-1503376780353-7e6692767b70"));
        cars.add(car("Maruti Suzuki", "Brezza", "KA03-MLP-016", 2023, "Petrol", "Automatic", 5, 3000, "Bengaluru", "Compact SUV", "Compact SUV with useful passenger and luggage room.", "https://images.unsplash.com/photo-1519641471654-76ce0107ad1b"));
        cars.add(car("Toyota", "Urban Cruiser Hyryder", "KA03-MLP-017", 2024, "Hybrid", "Automatic", 5, 4200, "Bengaluru", "SUV", "Hybrid mid-size SUV for efficient longer drives.", "https://images.unsplash.com/photo-1511919884226-fd3cad34687c"));
        cars.add(car("Mahindra", "XUV700", "KA03-MLP-018", 2023, "Diesel", "Automatic", 7, 5800, "Bengaluru", "SUV", "Three-row SUV with seating for up to seven.", "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf"));
        cars.add(car("Mahindra", "Scorpio-N", "KA03-MLP-019", 2024, "Diesel", "Manual", 7, 5400, "Bengaluru", "SUV", "Three-row SUV suited to group road journeys.", "https://images.unsplash.com/photo-1563720223185-11003d516935"));
        cars.add(car("Honda", "Amaze", "KA03-MLP-020", 2022, "Petrol", "Manual", 5, 2400, "Bengaluru", "Sedan", "Compact sedan for practical daily travel.", "https://images.unsplash.com/photo-1552519507-da3b142c6e3d"));
        cars.add(car("Renault", "Kwid", "KA03-MLP-021", 2023, "Petrol", "Manual", 5, 1600, "Bengaluru", "Hatchback", "Small city car with a raised driving position.", "https://images.unsplash.com/photo-1503376780353-7e6692767b70"));
        cars.add(car("Renault", "Kiger", "KA03-MLP-022", 2023, "Petrol", "Manual", 5, 2500, "Bengaluru", "Compact SUV", "Compact crossover for city and weekend use.", "https://images.unsplash.com/photo-1502161254066-6c74afbf07aa"));
        cars.add(car("Nissan", "Magnite", "KA03-MLP-023", 2024, "Petrol", "Manual", 5, 2600, "Bengaluru", "Compact SUV", "Compact crossover with a practical five-seat layout.", "https://images.unsplash.com/photo-1519641471654-76ce0107ad1b"));
        cars.add(car("Volkswagen", "Virtus", "KA03-MLP-024", 2023, "Petrol", "Automatic", 5, 3700, "Bengaluru", "Sedan", "Mid-size sedan suited to city and highway travel.", "https://images.unsplash.com/photo-1552519507-da3b142c6e3d"));
        cars.add(car("MG", "Astor", "KA03-MLP-025", 2023, "Petrol", "Automatic", 5, 3900, "Bengaluru", "SUV", "Mid-size SUV with a comfortable five-seat cabin.", "https://images.unsplash.com/photo-1511919884226-fd3cad34687c"));
        return List.copyOf(cars);
    }

    private static CarRequestDto car(String brand, String model, String registration, int year, String fuel,
            String transmission, int seats, int dailyRate, String location, String category, String details,
            String imageUrl) {
        CarRequestDto request = new CarRequestDto(brand, model, registration, year, fuel, transmission, seats,
                BigDecimal.valueOf(dailyRate), location, category + ". " + details + " Image is representative.");
        request.setImageUrl(imageUrl);
        return request;
    }
}
