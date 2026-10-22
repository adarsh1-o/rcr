# Remote Classroom for Rural Students (RCR Project)

A full-stack remote learning platform with a React and Vite frontend and a Spring Boot backend. It supports student, faculty, and administrator workspaces for course learning, assignments, live classes, attendance, and student support.

## Features

### For Students
- View and enroll in available courses
- Track course progress and attendance
- View assignments, submit answers, and view grades and feedback
- Download faculty-provided assignment resources
- Ask doubts and receive replies from faculty
- Join live video classes for enrolled courses
- Ask questions to an AI-powered educational assistant
- View notifications

### For Faculty
- Create and manage courses
- Add learning links (YouTube, Google Drive, PDF, etc.)
- Create and grade assignments
- Attach and manage assignment resource files
- Schedule and manage live video classes
- Mark attendance for enrolled students
- View student progress and completion rates
- Reply to student doubts

### For Admin
- Manage student and faculty users
- View system statistics and dashboard
- Monitor course enrollments and activity

## Tech Stack

### Backend
- **Java 17** with Spring Boot 4.1.0
- **Spring Security** with JWT authentication
- **Spring Data JPA** with Hibernate
- **MySQL** database by default; H2 can be selected for isolated local development and tests
- **Lombok** for reduced boilerplate

### Frontend
- **React 19** with Vite
- **React Router** for navigation
- **Axios** for API calls
- **React Hook Form** with Zod validation
- **Lucide React** for icons
- **Recharts** for data visualization

## Prerequisites

- Java 17 or higher
- Node.js 18 or higher
- MySQL 8.0 or higher (for production)

## Installation

### 1. Clone the repository
```bash
git clone https://github.com/Sushanth-16/rcr-project.git
cd rcr-project
```

### 2. Database Setup

The checked-in application configuration uses MySQL by default. Create the `demo` database configured in `demo/src/main/resources/application.properties`:

```sql
CREATE DATABASE demo;
```

Supply database credentials through your local configuration or environment. Do not commit credentials:
```properties
spring.datasource.username=your_username
spring.datasource.password=your_password
```

#### Environment Variables (Backend)

For the AI Assistant to work properly, export the following environment variable in your terminal before running Spring Boot:

```bash
export OPENAI_API_KEY="sk-your-openai-api-key"
export OPENAI_MODEL="gpt-3.5-turbo" # Optional, defaults to gpt-3.5-turbo
```

### 3. Backend Setup

```bash
cd demo
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

For a disposable local database, set the Spring datasource environment variables to an H2 in-memory URL and set `SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop` before starting the backend. This creates an empty database that is discarded when the process stops. Never point a test run at a database containing project data.

PowerShell example:
```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:mem:rcr_local;DB_CLOSE_DELAY=-1'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'create-drop'
./mvnw.cmd spring-boot:run
```

### 4. Frontend Setup

Open a new terminal:
```bash
cd frontend
npm install
npm run dev
```

The frontend will start on `http://localhost:5173`

If the backend runs on a different local port, set `VITE_API_PROXY_TARGET` before starting Vite (for example, `http://localhost:8082`). The default remains `http://localhost:8080`.

Public registration supports student and faculty accounts. Administrator accounts must be provisioned by an administrator; public registration cannot assign the administrator role. No default credentials are included in this repository.

## API Documentation

### Authentication
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login user

### Users (Admin)
- `GET /api/users` - Get all users
- `GET /api/users/role/{role}` - Get users by role
- `PATCH /api/users/{id}/active` - Activate/deactivate user

### Courses
- `GET /api/courses` - Get all courses
- `GET /api/courses/{id}` - Get course by ID
- `POST /api/courses` - Create course (Faculty)
- `PUT /api/courses/{id}` - Update course (Faculty)
- `DELETE /api/courses/{id}` - Delete course (Faculty)
- `POST /api/courses/{id}/links` - Add learning link (Faculty)

### Enrollments
- `POST /api/enrollments` - Enroll student in course
- `GET /api/enrollments/student/{studentId}` - Get student enrollments
- `GET /api/enrollments/course/{courseId}` - Get course enrollments

### Assignments
- `GET /api/assignments/course/{courseId}` - Get course assignments
- `POST /api/assignments` - Create assignment (Faculty)
- `PUT /api/assignments/{id}` - Update assignment (Faculty)
- `DELETE /api/assignments/{id}` - Delete assignment (Faculty)
- `GET /api/assignments/{id}/resources` - List authorized assignment resources
- `POST /api/assignments/{id}/resources` - Upload an assignment resource (Faculty)
- `GET /api/assignments/{id}/resources/{resourceId}/download` - Download an authorized resource

### Submissions
- `POST /api/submissions` - Submit assignment
- `GET /api/submissions/assignment/{assignmentId}` - Get assignment submissions
- `GET /api/submissions/student/{studentId}` - Get student submissions
- `PUT /api/submissions/{id}/grade` - Grade submission (Faculty)

### Attendance
- `POST /api/attendance` - Mark attendance (Faculty)
- `GET /api/attendance/student/{studentId}` - Get student attendance
- `GET /api/attendance/course/{courseId}` - Get course attendance

### Doubts
- `POST /api/doubts` - Create doubt (Student)
- `PUT /api/doubts/{id}/reply` - Reply to doubt (Faculty)
- `PUT /api/doubts/{id}/resolve` - Resolve doubt (Faculty)
- `GET /api/doubts/student/{studentId}` - Get student doubts
- `GET /api/doubts/faculty/{facultyId}` - Get faculty doubts
- `GET /api/doubts/course/{courseId}` - Get course doubts

### Notifications
- `GET /api/notifications/user/{userId}` - Get user notifications
- `PUT /api/notifications/{id}/read` - Mark notification as read
- `POST /api/notifications` - Create notification

### Live Meetings
- `POST /api/meetings` - Create a meeting (Faculty)
- `GET /api/meetings/course/{courseId}` - Get meetings by course
- `GET /api/meetings/{id}` - Get meeting details
- `PATCH /api/meetings/{id}/status` - Update meeting status (Faculty)
- `POST /api/meetings/{id}/join` - Join an authorized live meeting

API routes require JWT authentication unless explicitly permitted by `SecurityConfig`. Role and ownership checks are also enforced in backend services/controllers. Course and assignment data is not publicly available from the unauthenticated landing page.

### AI Assistant
- `POST /api/chatbot/message` - Send message to AI Chatbot
- `GET /api/chatbot/history/{studentId}` - Get chat history
- `DELETE /api/chatbot/history/{studentId}` - Clear chat history

### Dashboard
- `GET /api/dashboard/student/{studentId}` - Student dashboard
- `GET /api/dashboard/faculty/{facultyId}` - Faculty dashboard
- `GET /api/dashboard/admin` - Admin dashboard

## Project Structure

```
rcr-project/
├── demo/                    # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/example/demo/
│   │       ├── controller/  # REST API controllers
│   │       ├── service/     # Business logic
│   │       ├── repository/  # Data access layer
│   │       ├── entity/      # JPA entities
│   │       ├── dto/         # Data transfer objects
│   │       ├── security/    # Security configuration
│   │       └── exception/   # Exception handling
│   └── src/main/resources/
│       └── application.properties
│
└── frontend/                # React frontend
    ├── src/
    │   ├── pages/          # Page components
    │   │   ├── admin/
    │   │   ├── faculty/
    │   │   ├── student/
    │   │   └── public/
    │   ├── components/     # Reusable components
    │   ├── context/        # React context (auth)
    │   ├── services/       # API service layer
    │   └── layouts/        # Page layouts
    └── package.json
```

## License

MIT License
