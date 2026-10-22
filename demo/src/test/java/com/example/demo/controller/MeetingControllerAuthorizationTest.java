package com.example.demo.controller;

import com.example.demo.entity.Course;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeetingControllerAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    private User faculty;
    private User anotherFaculty;
    private Course course;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        faculty = userRepository.save(User.builder()
                .name("Meeting Faculty")
                .email("meeting-faculty-" + suffix + "@example.test")
                .password("x".repeat(60))
                .role(Role.FACULTY)
                .active(true)
                .build());
        anotherFaculty = userRepository.save(User.builder()
                .name("Other Faculty")
                .email("other-faculty-" + suffix + "@example.test")
                .password("x".repeat(60))
                .role(Role.FACULTY)
                .active(true)
                .build());
        course = courseRepository.save(Course.builder()
                .title("Meeting Authorization Course")
                .description("Authorization test")
                .faculty(faculty)
                .build());
    }

    @Test
    void facultyCanScheduleForOwnedCourseWithJwtRole() throws Exception {
        mockMvc.perform(post("/api/meetings")
                        .header("Authorization", bearerToken(faculty))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Scheduled from API"))
                .andExpect(jsonPath("$.courseId").value(course.getId()));
    }

    @Test
    void facultyCannotScheduleForAnotherFacultysCourse() throws Exception {
        mockMvc.perform(post("/api/meetings")
                        .header("Authorization", bearerToken(anotherFaculty))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can schedule classes only for courses you teach"));
    }

    @Test
    void facultyCourseListUsesTheAuthenticatedFacultyAndExcludesOtherOwners() throws Exception {
        Course otherCourse = courseRepository.save(Course.builder()
                .title("Other faculty course")
                .description("Must not be listed")
                .faculty(anotherFaculty)
                .build());

        mockMvc.perform(get("/api/courses/faculty/me")
                        .header("Authorization", bearerToken(faculty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(course.getId()))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(otherCourse.getId())));
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    private String requestBody() throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Scheduled from API",
                "description", "Authorization test",
                "courseId", course.getId(),
                "scheduledStartTime", Instant.now().plusSeconds(3600).toString()));
    }
}
