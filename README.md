# Car Rental Management System

A full-stack car rental management application built using React, Spring Boot, and PostgreSQL. The application allows customers to browse available vehicles, submit rental booking requests, and manage their bookings, while administrators manage vehicle inventory and booking operations.

## Features

### Customer Features
- Customer registration and login.
- Browse available vehicles and view rental prices.
- Search vehicle availability using pickup and return dates.
- Submit rental booking requests.
- View and manage personal bookings.
- Secure access to protected features using JWT authentication.

### Administrator Features
- Secure administrator authentication.
- Manage vehicle inventory.
- Review booking requests.
- Approve or reject booking requests.
- Manage booking operations and vehicle availability.

### Backend Features
- RESTful APIs built with Spring Boot.
- JWT-based authentication.
- Role-based authorization using Spring Security.
- Password hashing using BCrypt.
- PostgreSQL database integration.
- Database persistence using Spring Data JPA and Hibernate.
- Database schema migrations using Flyway.
- Booking date-overlap validation.
- Server-side rental price calculation.
- Centralized exception handling.
- Automated backend tests.

## Technology Stack

### Frontend
- React
- Vite
- JavaScript
- CSS

### Backend
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- Maven
- JWT

### Database and Tools
- PostgreSQL
- Flyway
- Git and GitHub
- Postman

## Project Structure

```text
carrentals/
├── frontend/
│   ├── public/
│   ├── src/
│   ├── package.json
│   └── package-lock.json
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── mvnw
├── .gitignore
└── README.md
```

The repository may contain additional configuration, test, and supporting files.

## Prerequisites

Install the following before running the application:

- Node.js and npm
- Java Development Kit (JDK) 21
- PostgreSQL
- Git

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Shanmukhpappala/carrentals.git
cd carrentals
```

### 2. Configure the Database

Create a PostgreSQL database for local development.

Configure the backend with the appropriate database URL, username, and password using the environment variables or local configuration required by the application.

Ensure that PostgreSQL is running before starting the backend.

Never commit real database credentials, administrator passwords, or JWT signing secrets.

### 3. Run the Backend

Open a terminal in the `backend` directory.

Ensure that Java 21 is installed and that the required database and security configuration is provided. Activate the Spring profile required by your local configuration, if applicable.

**Windows:**

```powershell
.\mvnw.cmd spring-boot:run
```

**macOS or Linux:**

```bash
./mvnw spring-boot:run
```

### 4. Run the Frontend

Open another terminal in the `frontend` directory, install the dependencies, and start the Vite development server:

```bash
npm install
npm run dev
```

Open the local URL printed by Vite in your browser. Ensure the frontend is configured to connect to the running backend API.
