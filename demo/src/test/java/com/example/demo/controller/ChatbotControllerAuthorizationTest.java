package com.example.demo.controller;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ChatbotProvider;
import com.example.demo.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotControllerAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private ChatbotProvider chatbotProvider;

    private User firstStudent;
    private User secondStudent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        firstStudent = saveStudent("first-" + suffix);
        secondStudent = saveStudent("second-" + suffix);
        when(chatbotProvider.generateReply(eq("Explain HDFS."), anyList(), anyBoolean()))
                .thenReturn("HDFS stores files across a cluster.");
    }

    @Test
    void chatRequiresAuthenticatedStudent() throws Exception {
        mockMvc.perform(get("/api/chatbot/history"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chatbot/history").header("Authorization", token(firstStudent)))
                .andExpect(status().isOk());
    }

    @Test
    void chatHistoryBelongsOnlyToTheAuthenticatedStudent() throws Exception {
        mockMvc.perform(post("/api/chatbot/message")
                        .header("Authorization", token(firstStudent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Explain HDFS.","studentId":%d}
                                """.formatted(secondStudent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("HDFS stores files across a cluster."));

        mockMvc.perform(get("/api/chatbot/history").header("Authorization", token(firstStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Explain HDFS."));

        mockMvc.perform(get("/api/chatbot/history").header("Authorization", token(secondStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/chatbot/history").header("Authorization", token(secondStudent)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/chatbot/history").header("Authorization", token(firstStudent)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void nonStudentsCannotUseStudentAssistantEndpoints() throws Exception {
        User faculty = userRepository.save(User.builder()
                .name("Faculty")
                .email("faculty-" + UUID.randomUUID() + "@example.test")
                .password("x".repeat(60))
                .role(Role.FACULTY)
                .active(true)
                .build());

        mockMvc.perform(get("/api/chatbot/history").header("Authorization", token(faculty)))
                .andExpect(status().isForbidden());
    }

    private User saveStudent(String emailPrefix) {
        return userRepository.save(User.builder()
                .name("Chat student")
                .email(emailPrefix + "@example.test")
                .password("x".repeat(60))
                .role(Role.STUDENT)
                .active(true)
                .build());
    }

    private String token(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
    }
}
