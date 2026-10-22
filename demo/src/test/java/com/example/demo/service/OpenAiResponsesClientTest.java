package com.example.demo.service;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class OpenAiResponsesClientTest {
    private static final String API_KEY = "sk-test-secret-not-for-output";
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenAiResponsesClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new OpenAiResponsesClient(restTemplate, API_KEY, "test-model", true);
    }

    @Test
    void sendsGeneralQuestionToResponsesAndExtractsAssistantText() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"model":"test-model","input":[{"role":"user","content":"Explain MapReduce simply."}]}
                        """, false))
                .andRespond(withSuccess("""
                        {"output":[{"type":"reasoning","summary":[]},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"MapReduce processes data in parallel."}]}]}
                        """, MediaType.APPLICATION_JSON));

        String answer = client.generateReply("Explain MapReduce simply.", List.of(
                message("USER", "Explain MapReduce simply.")), false);

        assertEquals("MapReduce processes data in parallel.", answer);
        assertFalse(answer.contains(API_KEY));
        server.verify();
    }

    @Test
    void sendsRecentConversationHistoryForFollowUpAndEnablesWebSearchForCurrentQuestions() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(content().json("""
                        {"input":[
                          {"role":"user","content":"Explain Big Data."},
                          {"role":"assistant","content":"It means datasets too large for traditional tools."},
                          {"role":"user","content":"What are its five Vs today?"}],
                         "tools":[{"type":"web_search","search_context_size":"medium"}]}
                        """, false))
                .andRespond(withSuccess("""
                        {"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"The five Vs are volume, velocity, variety, veracity, and value."}]}]}
                        """, MediaType.APPLICATION_JSON));

        String answer = client.generateReply("What are its five Vs today?", List.of(
                message("USER", "Explain Big Data."),
                message("BOT", "It means datasets too large for traditional tools."),
                message("USER", "What are its five Vs today?")), true);

        assertTrue(answer.contains("volume"));
        server.verify();
    }

    @Test
    void missingApiKeyReturnsSetupMessageWithoutMakingRequest() {
        OpenAiResponsesClient unconfigured = new OpenAiResponsesClient(restTemplate,
                " ", "test-model", true);

        ChatbotProviderException error = assertThrows(ChatbotProviderException.class,
                () -> unconfigured.generateReply("Hello", List.of(message("USER", "Hello")), false));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertTrue(error.getMessage().contains("OPENAI_API_KEY"));
        assertFalse(error.getMessage().contains(API_KEY));
        server.verify();
    }

    @Test
    void invalidCredentialAndUnavailableModelAreExplainedWithoutProviderDetails() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret diagnostic with credentials"));
        ChatbotProviderException credentialError = assertThrows(ChatbotProviderException.class,
                () -> client.generateReply("Hello", List.of(message("USER", "Hello")), false));
        assertTrue(credentialError.getMessage().contains("OPENAI_API_KEY"));
        assertFalse(credentialError.getMessage().contains("secret diagnostic"));

        server.reset();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("private provider error"));
        ChatbotProviderException modelError = assertThrows(ChatbotProviderException.class,
                () -> client.generateReply("Hello", List.of(message("USER", "Hello")), false));
        assertTrue(modelError.getMessage().contains("OPENAI_MODEL"));
        assertFalse(modelError.getMessage().contains("private provider error"));
    }

    @Test
    void rateLimitsAndTimeoutsReturnSafeActionableErrors() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        ChatbotProviderException rateLimit = assertThrows(ChatbotProviderException.class,
                () -> client.generateReply("Hello", List.of(message("USER", "Hello")), false));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimit.getStatus());

        server.reset();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withException(new IOException("connection details must stay private")));
        ChatbotProviderException timeout = assertThrows(ChatbotProviderException.class,
                () -> client.generateReply("Hello", List.of(message("USER", "Hello")), false));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, timeout.getStatus());
        assertFalse(timeout.getMessage().contains("connection details"));
    }

    @Test
    void webSearchCanBeDisabledWithoutClaimingCurrentInformationWasVerified() {
        OpenAiResponsesClient noSearchClient = new OpenAiResponsesClient(restTemplate,
                API_KEY, "test-model", false);
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.instructions", containsString("Web search is disabled for this request")))
                .andRespond(withSuccess("""
                        {"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"I cannot verify today's information without web search."}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(noSearchClient.generateReply("What is current today?",
                List.of(message("USER", "What is current today?")), true).contains("cannot verify"));
        server.verify();
    }

    private ChatMessage message(String sender, String content) {
        return ChatMessage.builder().sender(sender).content(content)
                .student(User.builder().id(7L).name("Test student").build()).build();
    }
}
