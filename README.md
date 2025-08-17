# Distributed Job Scheduler

A distributed job scheduling system with a Spring Boot backend and React TypeScript frontend.

## Project Structure

```
distributed-job-scheduler/
├── backend/                    # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/jobscheduler/
│   │   │   │   ├── controller/     # REST controllers
│   │   │   │   ├── service/        # Business logic
│   │   │   │   ├── repository/     # Data access layer
│   │   │   │   ├── model/          # Entity models
│   │   │   │   ├── config/         # Configuration classes
│   │   │   │   └── DistributedJobSchedulerApplication.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   └── pom.xml
├── frontend/                   # React TypeScript frontend
│   ├── src/
│   │   ├── components/         # Reusable components
│   │   ├── pages/              # Page components
│   │   ├── services/           # API services
│   │   ├── types/              # TypeScript type definitions
│   │   ├── hooks/              # Custom React hooks
│   │   ├── utils/              # Utility functions
│   │   ├── App.tsx
│   │   └── index.tsx
│   ├── package.json
│   └── .env
└── README.md
```

## Backend (Spring Boot)

### Features
- RESTful API endpoints
- Spring Security configuration
- JPA/Hibernate for database operations
- H2 database for development
- PostgreSQL support for production
- Spring Boot Actuator for monitoring

### Running the Backend
```bash
cd backend
./mvnw spring-boot:run
```

The backend will start on `http://localhost:8080`

### API Endpoints
- `GET /api/v1/jobs/health` - Health check endpoint
- `GET /api/v1/jobs` - Get all jobs
- `POST /api/v1/jobs` - Create a new job
- `GET /api/v1/jobs/{id}` - Get job by ID
- `PUT /api/v1/jobs/{id}` - Update job
- `DELETE /api/v1/jobs/{id}` - Delete job

## Frontend (React TypeScript)

### Features
- React 18 with TypeScript
- Material-UI for components
- React Router for navigation
- Axios for HTTP requests
- Custom hooks for state management
- Responsive design

### Running the Frontend
```bash
cd frontend
npm install
npm start
```

The frontend will start on `http://localhost:3000`

### Available Scripts
- `npm start` - Start development server
- `npm run build` - Build for production
- `npm test` - Run tests
- `npm run eject` - Eject from Create React App

## Development Setup

1. **Clone the repository**
2. **Set up the backend:**
   ```bash
   cd backend
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

3. **Set up the frontend:**
   ```bash
   cd frontend
   npm install
   npm start
   ```

4. **Access the applications:**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080
   - H2 Console: http://localhost:8080/h2-console

## Environment Variables

### Frontend (.env)
```
REACT_APP_API_URL=http://localhost:8080/api/v1
REACT_APP_APP_NAME=Distributed Job Scheduler
```

### Backend (application.properties)
- Database configuration
- Server port configuration
- Security settings
- Logging levels

## Technologies Used

### Backend
- Java 17
- Spring Boot 3.1.2
- Spring Security
- Spring Data JPA
- H2/PostgreSQL
- Maven

### Frontend
- React 18
- TypeScript
- Material-UI
- React Router
- Axios
- Create React App

## Next Steps

1. Implement job scheduling logic
2. Add authentication and authorization
3. Create job execution engine
4. Add real-time job status updates
5. Implement job queuing system
6. Add monitoring and logging
7. Set up CI/CD pipeline
