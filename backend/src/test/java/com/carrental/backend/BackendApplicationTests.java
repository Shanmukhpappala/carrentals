package com.carrental.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.carrental.backend.Exceptions.GlobalExceptionHandler;
import com.carrental.backend.entity.Booking;
import com.carrental.backend.entity.Car;
import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.BookingRepositary;
import com.carrental.backend.repositary.CarRepositary;
import com.carrental.backend.repositary.UserRepositary;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = com.carrental.backend.config.TestDatabaseIsolationValidator.class)
class BackendApplicationTests {

	private static final String FRONTEND_ORIGIN = "http://localhost:5173";

	@Value("${local.server.port}")
	private int port;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Value("${app.admin.password}")
	private String adminPassword;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BookingRepositary bookingRepository;

	@Autowired
	private CarRepositary carRepository;

	@Autowired
	private UserRepositary userRepository;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void testOnlySecurityProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TestIntegrationProperties.JWT_SECRET);
		registry.add("app.admin.email", () -> TestIntegrationProperties.ADMIN_EMAIL);
		registry.add("app.admin.password", () -> TestIntegrationProperties.ADMIN_PASSWORD);
		registry.add("app.admin.bootstrap.enabled", () -> "true");
		registry.add("app.admin.bootstrap.rotate-existing", () -> "false");
	}

	@Test
	void contextLoads() {
	}

	@Test
	void corsPreflightAllowsConfiguredFrontendForLogin() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/auth/login")))
				.header("Origin", FRONTEND_ORIGIN)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,authorization")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertEquals(FRONTEND_ORIGIN,
				response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
		String allowedMethods = response.headers().firstValue("Access-Control-Allow-Methods").orElse("");
		for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) {
			assertTrue(allowedMethods.contains(method), "Missing allowed method: " + method);
		}
		String allowedHeaders = response.headers().firstValue("Access-Control-Allow-Headers").orElse("");
		String normalizedHeaders = allowedHeaders.toLowerCase(Locale.ROOT);
		assertTrue(normalizedHeaders.contains("content-type"));
		assertTrue(normalizedHeaders.contains("authorization"));
	}

	@Test
	void protectedEndpointStillRejectsUnauthenticatedRequest() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/users/me")))
				.header("Origin", FRONTEND_ORIGIN)
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(401, response.statusCode());
		assertEquals(FRONTEND_ORIGIN,
				response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
	}

	@Test
	void malformedAndExpiredBearerTokensReturnUnauthorized() throws Exception {
		HttpResponse<String> malformed = send("GET", "/api/users/me", null, "not-a-valid-token");
		assertErrorResponse(malformed, 401);

		long now = System.currentTimeMillis();
		String expiredToken = Jwts.builder()
				.subject(adminEmail)
				.issuedAt(new Date(now - 60_000))
				.expiration(new Date(now - 1_000))
				.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
				.compact();
		HttpResponse<String> expired = send("GET", "/api/users/me", null, expiredToken);
		assertErrorResponse(expired, 401);
		assertFalse(expired.body().contains(expiredToken));
	}

	@Test
	void corsPreflightRejectsUnconfiguredFrontendOrigin() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/auth/login")))
				.header("Origin", "https://untrusted.example")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,authorization")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(403, response.statusCode());
		assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
	}

	@Test
	void malformedJsonReturnsSafeBadRequest() throws Exception {
		HttpResponse<String> response = send("POST", "/api/auth/register", "{", null);

		assertErrorResponse(response, 400);
		assertTrue(response.body().contains("Malformed request body"));
	}

	@Test
	void registrationValidationReturnsSafeBadRequest() throws Exception {
		HttpResponse<String> missingFields = send("POST", "/api/auth/register", "{}", null);
		assertErrorResponse(missingFields, 400);

		String invalidEmail = "{\"name\":\"Audit\",\"email\":\"not-an-email\","
				+ "\"password\":\"valid-password\",\"phone\":\"5550000000\"}";
		HttpResponse<String> invalidEmailResponse = send("POST", "/api/auth/register", invalidEmail, null);
		assertErrorResponse(invalidEmailResponse, 400);

		String shortPassword = "{\"name\":\"Audit\",\"email\":\"short-password@example.test\","
				+ "\"password\":\"x\",\"phone\":\"5550000000\"}";
		HttpResponse<String> shortPasswordResponse = send("POST", "/api/auth/register", shortPassword, null);
		assertErrorResponse(shortPasswordResponse, 400);
	}

	@Test
	void nonexistentAndInvalidCredentialLoginsUseSameGenericUnauthorizedResponse() throws Exception {
		String unknownEmail = "missing-" + UUID.randomUUID() + "@example.test";
		String unknownLogin = "{\"email\":" + jsonString(unknownEmail)
				+ ",\"password\":\"audit-invalid-secret\"}";
		HttpResponse<String> unknownResponse = send("POST", "/api/auth/login", unknownLogin, null);

		String badPasswordLogin = "{\"email\":" + jsonString(adminEmail)
				+ ",\"password\":\"audit-invalid-secret\"}";
		HttpResponse<String> badPasswordResponse = send("POST", "/api/auth/login", badPasswordLogin, null);

		assertErrorResponse(unknownResponse, 401);
		assertErrorResponse(badPasswordResponse, 401);
		assertEquals(errorMessage(badPasswordResponse.body()), errorMessage(unknownResponse.body()));
		assertEquals("Invalid email or password", errorMessage(unknownResponse.body()));
		assertFalse(unknownResponse.body().contains("audit-invalid-secret"));
	}

	@Test
	void duplicateCustomerRegistrationReturnsConflict() throws Exception {
		String duplicateRegistration = "{\"name\":\"Audit duplicate\",\"email\":"
				+ jsonString(adminEmail)
				+ ",\"password\":\"audit-invalid-secret\",\"phone\":\"5550000000\"}";
		HttpResponse<String> response = send("POST", "/api/auth/register", duplicateRegistration, null);

		assertErrorResponse(response, 409);
		assertFalse(response.body().contains("audit-invalid-secret"));
	}

	@Test
	void concurrentCaseVariantCustomerRegistrationsAllowOnlyOne() throws Exception {
		String email = "audit-race-" + UUID.randomUUID() + "@example.test";
		String upperEmail = email.toUpperCase(Locale.ROOT);
		String lowerRequest = registerRequest(email);
		String upperRequest = registerRequest(upperEmail);
		try {
			List<HttpResponse<String>> responses = sendConcurrently(
					"/api/auth/register", lowerRequest, upperRequest, null);

			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 201).count());
			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 409).count());
			Long userId = idFromBody(responses.stream()
					.filter(response -> response.statusCode() == 201).findFirst().orElseThrow().body());
			assertNotNull(userId);
		} finally {
			List<Long> testUserIds = jdbcTemplate.query(
					"SELECT id FROM app_user WHERE lower(btrim(email)) = ?",
					(resultSet, rowNumber) -> resultSet.getLong("id"), email.toLowerCase(Locale.ROOT));
			userRepository.deleteAllById(testUserIds);
		}
	}

	@Test
	void concurrentCaseVariantCarCreationsAllowOnlyOne() throws Exception {
		String registration = "AUDIT-" + UUID.randomUUID();
		String adminToken = adminToken();
		try {
			List<HttpResponse<String>> responses = sendConcurrently(
					"/api/admin/cars",
					carRequest(registration, "99.99"),
					carRequest(registration.toLowerCase(Locale.ROOT), "99.99"),
					adminToken);

			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 201).count());
			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 409).count());
		} finally {
			List<Long> testCarIds = jdbcTemplate.query(
					"SELECT id FROM car WHERE upper(btrim(registration_number)) = ?",
					(resultSet, rowNumber) -> resultSet.getLong("id"), registration.toUpperCase(Locale.ROOT));
			carRepository.deleteAllById(testCarIds);
		}
	}

	@Test
	void carUpdateRejectsCaseVariantDuplicateAndCanonicalizesItsOwnRegistration() throws Exception {
		String existingRegistration = "AUDIT-EXISTING-" + UUID.randomUUID();
		String updatedRegistration = "AUDIT-UPDATE-" + UUID.randomUUID();
		String adminToken = adminToken();
		Long existingCarId = null;
		Long updatedCarId = null;
		try {
			HttpResponse<String> existingCar = send("POST", "/api/admin/cars",
					carRequest(existingRegistration, "99.99"), adminToken);
			assertEquals(201, existingCar.statusCode());
			existingCarId = idFromBody(existingCar.body());

			HttpResponse<String> updatedCar = send("POST", "/api/admin/cars",
					carRequest(updatedRegistration, "99.99"), adminToken);
			assertEquals(201, updatedCar.statusCode());
			updatedCarId = idFromBody(updatedCar.body());

			HttpResponse<String> duplicateUpdate = send("PUT", "/api/admin/cars/" + updatedCarId,
					carRequest(existingRegistration.toLowerCase(Locale.ROOT), "99.99"), adminToken);
			assertErrorResponse(duplicateUpdate, 409);

			HttpResponse<String> caseOnlyUpdate = send("PUT", "/api/admin/cars/" + updatedCarId,
					carRequest(updatedRegistration.toLowerCase(Locale.ROOT), "99.99"), adminToken);
			assertEquals(200, caseOnlyUpdate.statusCode());
			assertTrue(caseOnlyUpdate.body().contains(updatedRegistration.toUpperCase(Locale.ROOT)));
		} finally {
			if (updatedCarId != null) {
				carRepository.deleteById(updatedCarId);
			}
			if (existingCarId != null) {
				carRepository.deleteById(existingCarId);
			}
		}
	}

	@Test
	void imageUrlRoundTripsAndRemainsAdminOnlyOverHttp() throws Exception {
		String registration = "IMAGE-" + UUID.randomUUID();
		String noImageRegistration = "NO-IMAGE-" + UUID.randomUUID();
		String deniedRegistration = "DENIED-IMAGE-" + UUID.randomUUID();
		String customerEmail = "audit-image-" + UUID.randomUUID() + "@example.test";
		String imageUrl = "https://images.example/vehicle.jpg";
		String replacementUrl = "https://images.example/vehicle-updated.jpg";
		String adminToken = adminToken();
		Long carId = null;
		Long noImageCarId = null;
		Long customerId = null;
		try {
			HttpResponse<String> created = send("POST", "/api/admin/cars",
					carRequest(registration, "99.99", imageUrl), adminToken);
			assertEquals(201, created.statusCode());
			carId = idFromBody(created.body());
			assertTrue(created.body().contains("\"imageUrl\":" + jsonString(imageUrl)));
			assertEquals(imageUrl, carRepository.findById(carId).orElseThrow().getImageUrl());

			HttpResponse<String> detail = send("GET", "/api/cars/" + carId, null, null);
			assertEquals(200, detail.statusCode());
			assertTrue(detail.body().contains("\"imageUrl\":" + jsonString(imageUrl)));

			HttpResponse<String> unrelatedUpdate = send("PUT", "/api/admin/cars/" + carId,
					carRequest(registration, "100.00"), adminToken);
			assertEquals(200, unrelatedUpdate.statusCode());
			assertEquals(imageUrl, carRepository.findById(carId).orElseThrow().getImageUrl());

			HttpResponse<String> replacement = send("PUT", "/api/admin/cars/" + carId,
					carRequest(registration, "100.00", replacementUrl), adminToken);
			assertEquals(200, replacement.statusCode());
			assertTrue(replacement.body().contains("\"imageUrl\":" + jsonString(replacementUrl)));
			assertEquals(replacementUrl, carRepository.findById(carId).orElseThrow().getImageUrl());

			HttpResponse<String> cleared = send("PUT", "/api/admin/cars/" + carId,
					carRequest(registration, "100.00", ""), adminToken);
			assertEquals(200, cleared.statusCode());
			assertTrue(cleared.body().contains("\"imageUrl\":null"));
			assertEquals(null, carRepository.findById(carId).orElseThrow().getImageUrl());

			HttpResponse<String> createdWithoutImage = send("POST", "/api/admin/cars",
					carRequest(noImageRegistration, "99.99"), adminToken);
			assertEquals(201, createdWithoutImage.statusCode());
			noImageCarId = idFromBody(createdWithoutImage.body());
			assertEquals(null, carRepository.findById(noImageCarId).orElseThrow().getImageUrl());

			HttpResponse<String> invalidUrl = send("POST", "/api/admin/cars",
					carRequest("INVALID-IMAGE-" + UUID.randomUUID(), "99.99", "javascript:alert(1)"), adminToken);
			assertErrorResponse(invalidUrl, 400);

			HttpResponse<String> registeredCustomer = send("POST", "/api/auth/register",
					registerRequest(customerEmail), null);
			assertEquals(201, registeredCustomer.statusCode());
			customerId = idFromBody(registeredCustomer.body());
			String customerToken = customerToken(customerEmail);
			assertErrorResponse(send("POST", "/api/admin/cars",
					carRequest(deniedRegistration, "99.99", imageUrl), customerToken), 403);
			assertErrorResponse(send("PUT", "/api/admin/cars/" + carId,
					carRequest(registration, "100.00", imageUrl), customerToken), 403);
		} finally {
			if (noImageCarId != null) {
				carRepository.deleteById(noImageCarId);
			}
			if (carId != null) {
				carRepository.deleteById(carId);
			}
			if (customerId != null) {
				userRepository.deleteById(customerId);
			}
		}
	}

	@Test
	void customerRegistrationProfileAvailabilityBookingAndOwnershipWorkOverHttp() throws Exception {
		String customerEmail = "audit-customer-journey-" + UUID.randomUUID() + "@example.test";
		String otherCustomerEmail = "audit-other-customer-" + UUID.randomUUID() + "@example.test";
		String registration = "AUDIT-JOURNEY-" + UUID.randomUUID();
		String adminToken = adminToken();
		Long customerId = null;
		Long otherCustomerId = null;
		Long carId = null;
		Long bookingId = null;
		try {
			HttpResponse<String> registrationResponse = send("POST", "/api/auth/register",
					registerRequest(customerEmail), null);
			assertEquals(201, registrationResponse.statusCode());
			assertTrue(registrationResponse.body().contains("\"role\":\"CUSTOMER\""));
			customerId = idFromBody(registrationResponse.body());
			String customerToken = customerToken(customerEmail);

			HttpResponse<String> profile = send("GET", "/api/users/me", null, customerToken);
			assertEquals(200, profile.statusCode());
			assertTrue(profile.body().contains(customerEmail));
			HttpResponse<String> updatedProfile = send("PUT", "/api/users/me",
					"{\"name\":\"Updated audit customer\",\"phone\":\"5551112222\"}", customerToken);
			assertEquals(200, updatedProfile.statusCode());
			assertTrue(updatedProfile.body().contains("Updated audit customer"));

			HttpResponse<String> createdCar = send("POST", "/api/admin/cars",
					carRequest(registration, "99.99"), adminToken);
			assertEquals(201, createdCar.statusCode());
			carId = idFromBody(createdCar.body());
			assertEquals(200, send("GET", "/api/cars/" + carId, null, null).statusCode());

			LocalDate pickup = LocalDate.now().plusDays(30);
			LocalDate returnDate = pickup.plusDays(1);
			String dateQuery = "?pickupDate=" + pickup + "&returnDate=" + returnDate;
			HttpResponse<String> availableBeforeBooking = send("GET", "/api/cars" + dateQuery, null, null);
			assertEquals(200, availableBeforeBooking.statusCode());
			assertTrue(responseContainsId(availableBeforeBooking.body(), carId));

			String requestBody = "{\"carId\":" + carId + ",\"pickupDate\":" + jsonString(pickup.toString())
					+ ",\"returnDate\":" + jsonString(returnDate.toString()) + "}";
			HttpResponse<String> createdBooking = send("POST", "/api/bookings", requestBody, customerToken);
			assertEquals(201, createdBooking.statusCode());
			assertTrue(createdBooking.body().contains("\"status\":\"REQUESTED\""));
			bookingId = idFromBody(createdBooking.body());
			assertEquals(200, send("GET", "/api/bookings/my", null, customerToken).statusCode());
			assertEquals(200, send("GET", "/api/bookings/" + bookingId, null, customerToken).statusCode());

			HttpResponse<String> unavailableWhileRequested = send("GET", "/api/cars" + dateQuery, null, null);
			assertFalse(responseContainsId(unavailableWhileRequested.body(), carId));

			HttpResponse<String> otherRegistration = send("POST", "/api/auth/register",
					registerRequest(otherCustomerEmail), null);
			assertEquals(201, otherRegistration.statusCode());
			otherCustomerId = idFromBody(otherRegistration.body());
			String otherCustomerToken = customerToken(otherCustomerEmail);
			assertErrorResponse(send("GET", "/api/bookings/" + bookingId, null, otherCustomerToken), 404);
			assertErrorResponse(send("PATCH", "/api/bookings/" + bookingId + "/cancel", null,
					otherCustomerToken), 404);

			HttpResponse<String> cancelledBooking = send("PATCH", "/api/bookings/" + bookingId + "/cancel",
					null, customerToken);
			assertEquals(200, cancelledBooking.statusCode());
			assertTrue(cancelledBooking.body().contains("\"status\":\"CANCELLED\""));
			HttpResponse<String> availableAfterCancellation = send("GET", "/api/cars" + dateQuery, null, null);
			assertTrue(responseContainsId(availableAfterCancellation.body(), carId));
		} finally {
			if (bookingId != null) bookingRepository.deleteById(bookingId);
			if (carId != null) carRepository.deleteById(carId);
			if (otherCustomerId != null) userRepository.deleteById(otherCustomerId);
			if (customerId != null) userRepository.deleteById(customerId);
		}
	}

	@Test
	void adminCarManagementAndBookingTransitionsWorkOverHttp() throws Exception {
		String adminToken = adminToken();
		String registration = "AUDIT-ADMIN-JOURNEY-" + UUID.randomUUID();
		Long carId = null;
		List<BookingFixture> fixtures = new java.util.ArrayList<>();
		try {
			HttpResponse<String> createdCar = send("POST", "/api/admin/cars",
					carRequest(registration, "99.99"), adminToken);
			assertEquals(201, createdCar.statusCode());
			carId = idFromBody(createdCar.body());

			HttpResponse<String> updatedCar = send("PUT", "/api/admin/cars/" + carId,
					carRequest(registration, "109.99"), adminToken);
			assertEquals(200, updatedCar.statusCode());
			assertTrue(updatedCar.body().contains("109.99"));
			assertEquals(200, send("PATCH", "/api/admin/cars/" + carId + "/status",
					"{\"status\":\"MAINTENANCE\"}", adminToken).statusCode());
			HttpResponse<String> unavailableCar = send("GET", "/api/cars", null, null);
			assertFalse(responseContainsId(unavailableCar.body(), carId));
			assertEquals(200, send("PATCH", "/api/admin/cars/" + carId + "/status",
					"{\"status\":\"AVAILABLE\"}", adminToken).statusCode());

			assertEquals(200, send("GET", "/api/admin/bookings", null, adminToken).statusCode());
			for (String status : List.of("REQUESTED", "REQUESTED", "REQUESTED", "CONFIRMED", "CONFIRMED", "ACTIVE")) {
				fixtures.add(createBookingFixture(status));
			}
			String[] actions = {"approve", "reject", "cancel", "pickup", "no-show", "complete"};
			String[] expectedStatuses = {"CONFIRMED", "REJECTED", "CANCELLED", "ACTIVE", "NO_SHOW", "COMPLETED"};
			for (int index = 0; index < fixtures.size(); index++) {
				Long bookingId = fixtures.get(index).booking().getId();
				HttpResponse<String> transition = send("PATCH",
						"/api/admin/bookings/" + bookingId + "/" + actions[index], null, adminToken);
				assertEquals(200, transition.statusCode(), actions[index]);
				assertTrue(transition.body().contains("\"status\":\"" + expectedStatuses[index] + "\""));
			}
		} finally {
			for (BookingFixture fixture : fixtures) deleteBookingFixture(fixture);
			if (carId != null) carRepository.deleteById(carId);
		}
	}

	@Test
	void adminCanCancelConfirmedBookingOverHttp() throws Exception {
		String adminToken = adminToken();
		BookingFixture fixture = createBookingFixture("CONFIRMED");
		try {
			HttpResponse<String> response = send("PATCH",
					"/api/admin/bookings/" + fixture.booking().getId() + "/cancel", null, adminToken);

			assertEquals(200, response.statusCode());
			assertTrue(response.body().contains("\"status\":\"CANCELLED\""));
			assertEquals("CANCELLED", bookingRepository.findById(fixture.booking().getId()).orElseThrow().getStatus());
		} finally {
			deleteBookingFixture(fixture);
		}
	}

	@Test
	void concurrentConflictingAdminTransitionsLeaveOneValidFinalState() throws Exception {
		String adminToken = adminToken();
		BookingFixture fixture = createBookingFixture("REQUESTED");
		try {
			Long bookingId = fixture.booking().getId();
			List<HttpResponse<String>> responses = sendConcurrentlyPatches(
					"/api/admin/bookings/" + bookingId + "/cancel",
					"/api/admin/bookings/" + bookingId + "/reject",
					adminToken);

			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 200).count());
			assertEquals(1, responses.stream().filter(response -> response.statusCode() == 400).count());
			String finalStatus = bookingRepository.findById(bookingId).orElseThrow().getStatus();
			assertTrue(List.of("CANCELLED", "REJECTED").contains(finalStatus));
		} finally {
			deleteBookingFixture(fixture);
		}
	}

	@Test
	void duplicateCarRegistrationAndInvalidPriceReturnClientErrors() throws Exception {
		Car fixture = new Car();
		String registrationNumber = "AUDIT-DUPLICATE-" + UUID.randomUUID();
		fixture.setBrand("Audit");
		fixture.setModel("Duplicate registration fixture");
		fixture.setRegistrationNumber(registrationNumber);
		fixture.setYear(2026);
		fixture.setFuelType("Hybrid");
		fixture.setTransmission("Automatic");
		fixture.setSeatingCapacity(5);
		fixture.setPricePerDay(new BigDecimal("99.99"));
		fixture.setLocation("Local");
		fixture.setStatus("AVAILABLE");
		fixture.setDescription("HTTP integration fixture");
		fixture = carRepository.save(fixture);
		try {
			HttpResponse<String> cars = send("GET", "/api/cars", null, null);
			assertEquals(200, cars.statusCode());
			Matcher fixtureMatcher = Pattern.compile("\"registrationNumber\"\\s*:\\s*\""
					+ Pattern.quote(registrationNumber) + "\"").matcher(cars.body());
			assertTrue(fixtureMatcher.find(), "Expected the available fixture car in public listing");
			String adminToken = adminToken();

			HttpResponse<String> duplicateResponse = send("POST", "/api/admin/cars",
					carRequest(registrationNumber, "99.99"), adminToken);
			assertErrorResponse(duplicateResponse, 409);

			HttpResponse<String> invalidPriceResponse = send("POST", "/api/admin/cars",
					carRequest(registrationNumber, "-1.00"), adminToken);
			assertErrorResponse(invalidPriceResponse, 400);
		} finally {
			carRepository.deleteById(fixture.getId());
		}
	}

	@Test
	void nonexistentCarReturnsSafeNotFound() throws Exception {
		HttpResponse<String> response = send("GET", "/api/cars/999999997", null, null);

		assertErrorResponse(response, 404);
		assertTrue(errorMessage(response.body()).contains("Car not found"));
	}

	@Test
	void dataIntegrityViolationsReturnGenericConflict() {
		var response = new GlobalExceptionHandler().handleDataIntegrityViolation(
				new DataIntegrityViolationException("constraint-secret-detail"));

		assertEquals(409, response.getStatusCode().value());
		assertEquals("Request conflicts with existing data", response.getBody().get("message"));
		assertFalse(response.getBody().toString().contains("constraint-secret-detail"));
	}

	private HttpResponse<String> send(String method, String path, String body, String token)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
				.header("Origin", FRONTEND_ORIGIN);
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		HttpRequest.BodyPublisher publisher = body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body);
		return httpClient.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String adminToken() throws Exception {
		String body = "{\"email\":" + jsonString(adminEmail)
				+ ",\"password\":" + jsonString(adminPassword) + "}";
		HttpResponse<String> response = send("POST", "/api/admin/auth/login", body, null);
		assertEquals(200, response.statusCode());
		Matcher tokenMatcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(response.body());
		assertTrue(tokenMatcher.find(), "Expected admin login response to include a token");
		return tokenMatcher.group(1);
	}

	private String customerToken(String email) throws Exception {
		String body = "{\"email\":" + jsonString(email)
				+ ",\"password\":\"audit-valid-secret\"}";
		HttpResponse<String> response = send("POST", "/api/auth/login", body, null);
		assertEquals(200, response.statusCode());
		Matcher tokenMatcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(response.body());
		assertTrue(tokenMatcher.find(), "Expected customer login response to include a token");
		return tokenMatcher.group(1);
	}

	private String carRequest(String registrationNumber, String pricePerDay) {
		return "{\"brand\":\"Audit\",\"model\":\"Validation Car\",\"registrationNumber\":"
				+ jsonString(registrationNumber)
				+ ",\"year\":2026,\"fuelType\":\"Hybrid\",\"transmission\":\"Automatic\","
				+ "\"seatingCapacity\":5,\"pricePerDay\":" + pricePerDay
				+ ",\"location\":\"Local\",\"description\":\"Validation test\"}";
	}

	private String carRequest(String registrationNumber, String pricePerDay, String imageUrl) {
		return carRequest(registrationNumber, pricePerDay).replace(
				"\"description\":\"Validation test\"}",
				"\"imageUrl\":" + jsonString(imageUrl) + ",\"description\":\"Validation test\"}");
	}

	private String registerRequest(String email) {
		return "{\"name\":\"Audit concurrent\",\"email\":" + jsonString(email)
				+ ",\"password\":\"audit-valid-secret\",\"phone\":\"5550000000\"}";
	}

	private List<HttpResponse<String>> sendConcurrently(String path, String firstBody, String secondBody,
			String token) throws Exception {
		CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(
				() -> sendUnchecked("POST", path, firstBody, token));
		CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(
				() -> sendUnchecked("POST", path, secondBody, token));		
		return List.of(first.get(), second.get());
	}

	private List<HttpResponse<String>> sendConcurrentlyPatches(String firstPath, String secondPath, String token)
			throws Exception {
		CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(
				() -> sendUnchecked("PATCH", firstPath, null, token));
		CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(
				() -> sendUnchecked("PATCH", secondPath, null, token));
		return List.of(first.get(), second.get());
	}

	private HttpResponse<String> sendUnchecked(String method, String path, String body, String token) {
		try {
			return send(method, path, body, token);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(exception);
		}
	}

	private Long idFromBody(String body) {
		Matcher idMatcher = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)").matcher(body);
		assertTrue(idMatcher.find(), "Expected response to include an id");
		return Long.valueOf(idMatcher.group(1));
	}

	private boolean responseContainsId(String body, Long id) {
		return Pattern.compile("\\\"id\\\"\\s*:\\s*" + id + "(?:,|})").matcher(body).find();
	}

	private BookingFixture createBookingFixture(String status) {
		String suffix = UUID.randomUUID().toString();
		User user = new User();
		user.setName("Audit booking user");
		user.setEmail("audit-booking-" + suffix + "@example.test");
		user.setPassword("not-used");
		user.setPhone("5550000000");
		user.setRole("CUSTOMER");
		user = userRepository.save(user);

		Car car = new Car();
		car.setBrand("Audit");
		car.setModel("Booking fixture");
		car.setRegistrationNumber("AUDIT-BOOKING-" + suffix);
		car.setYear(2026);
		car.setFuelType("Hybrid");
		car.setTransmission("Automatic");
		car.setSeatingCapacity(5);
		car.setPricePerDay(new BigDecimal("99.99"));
		car.setLocation("Local");
		car.setStatus("AVAILABLE");
		car.setDescription("HTTP integration fixture");
		car = carRepository.save(car);

		Booking booking = new Booking();
		booking.setUser(user);
		booking.setCar(car);
		booking.setPickupDate(LocalDate.now().plusDays(20));
		booking.setReturnDate(LocalDate.now().plusDays(22));
		booking.setTotalAmount(new BigDecimal("299.97"));
		booking.setStatus(status);
		booking.setCreatedAt(LocalDate.now());
		booking = bookingRepository.save(booking);
		return new BookingFixture(user, car, booking);
	}

	private void deleteBookingFixture(BookingFixture fixture) {
		bookingRepository.deleteById(fixture.booking().getId());
		carRepository.deleteById(fixture.car().getId());
		userRepository.deleteById(fixture.user().getId());
	}

	private record BookingFixture(User user, Car car, Booking booking) {
	}

	private String jsonString(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private void assertErrorResponse(HttpResponse<String> response, int status) {
		assertEquals(status, response.statusCode());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
		assertTrue(response.body().contains("\"timestamp\""));
		assertTrue(response.body().contains("\"error\""));
		assertTrue(response.body().contains("\"message\""));
		assertTrue(Pattern.compile("\"status\"\\s*:\\s*" + status).matcher(response.body()).find());
		String lowerBody = response.body().toLowerCase(Locale.ROOT);
		assertFalse(lowerBody.contains("stacktrace"));
		assertFalse(lowerBody.contains("java.lang."));
		assertFalse(response.body().contains("audit-invalid-secret"));
		assertFalse(response.body().contains("constraint-secret-detail"));
	}

	private String errorMessage(String body) {
		Matcher messageMatcher = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
		assertTrue(messageMatcher.find(), "Expected JSON error message");
		return messageMatcher.group(1);
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
