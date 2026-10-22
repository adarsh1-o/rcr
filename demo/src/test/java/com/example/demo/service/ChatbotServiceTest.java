package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotServiceTest {
    private final FakeChatStore store = new FakeChatStore();
    private final RecordingProvider provider = new RecordingProvider();
    private ChatbotService chatbotService;
    private User student;

    @BeforeEach
    void setUp() {
        ChatMessageRepository chatMessages = proxy(ChatMessageRepository.class, (method, args) -> switch (method) {
            case "save" -> store.save((ChatMessage) args[0]);
            case "findTop12ByStudentIdOrderByCreatedAtDesc" -> store.latest((Long) args[0]);
            case "findByStudentIdOrderByCreatedAtAsc" -> store.history((Long) args[0]);
            case "deleteByStudentId" -> { store.clear((Long) args[0]); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
        UserRepository users = proxy(UserRepository.class, (method, args) -> {
            if (method.equals("existsById")) return student != null && student.getId().equals(args[0]);
            throw new UnsupportedOperationException(method);
        });
        chatbotService = new ChatbotService(chatMessages, users, provider, new ChatbotRateLimiter());
        student = User.builder().id(12L).name("Student").email("student@example.test").role(Role.STUDENT).build();
    }

    @Test
    void generalQuestionIsSentToProviderAndAnswerIsPersistedForAuthenticatedStudent() {
        provider.answer = "HDFS stores large files across multiple machines.";

        var response = chatbotService.processMessage(request("Explain HDFS."), student);

        assertEquals("HDFS stores large files across multiple machines.", response.getReply());
        assertEquals(12L, response.getStudentId());
        assertEquals("Explain HDFS.", provider.message);
        assertEquals(List.of("Explain HDFS."), contents(provider.context));
        assertEquals(2, store.history(12L).size());
    }

    @Test
    void followUpUsesOnlyTheMostRecentMessagesInChronologicalOrder() {
        store.save(message("USER", "Explain Big Data."));
        store.save(message("BOT", "Big Data is data too large for traditional tools."));
        provider.answer = "Volume, velocity, variety, veracity, and value.";

        var response = chatbotService.processMessage(request("What are its five Vs?"), student);

        assertTrue(response.getReply().contains("veracity"));
        assertEquals(List.of("Explain Big Data.", "Big Data is data too large for traditional tools.",
                "What are its five Vs?"), contents(provider.context));
    }

    @Test
    void requestForCurrentInformationEnablesSearch() {
        provider.answer = "I checked current sources.";

        chatbotService.processMessage(request("What is the latest Java release today?"), student);

        assertTrue(provider.useWebSearch);
    }

    @Test
    void nonStudentCannotUseAssistantService() {
        User faculty = User.builder().id(2L).role(Role.FACULTY).build();
        assertThrows(AccessDeniedException.class,
                () -> chatbotService.processMessage(request("Hello"), faculty));
        assertEquals(0, provider.calls);
        assertTrue(store.history(12L).isEmpty());
    }

    @Test
    void historyIsReturnedAsSafeMessageDtosAndClearUsesOnlyAuthenticatedStudentId() {
        store.save(message("USER", "My question"));

        var history = chatbotService.getHistory(student);
        chatbotService.clearHistory(student);

        assertEquals(1, history.size());
        assertEquals("My question", history.get(0).getContent());
        assertTrue(store.history(12L).isEmpty());
    }

    private static List<String> contents(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::getContent).toList();
    }

    private ChatRequest request(String message) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        return request;
    }

    private ChatMessage message(String sender, String content) {
        return ChatMessage.builder().sender(sender).content(content).student(student).build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyCall handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.getName().equals("toString") ? type.getSimpleName() + " test double" : null;
                    }
                    return handler.call(method.getName(), args == null ? new Object[0] : args);
                });
    }

    @FunctionalInterface
    private interface ProxyCall {
        Object call(String method, Object[] args);
    }

    private static final class RecordingProvider implements ChatbotProvider {
        private String answer = "Test answer";
        private String message;
        private List<ChatMessage> context = List.of();
        private boolean useWebSearch;
        private int calls;

        @Override
        public String generateReply(String message, List<ChatMessage> recentMessages, boolean useWebSearch) {
            this.message = message;
            this.context = recentMessages;
            this.useWebSearch = useWebSearch;
            calls++;
            return answer;
        }
    }

    private static final class FakeChatStore {
        private final List<ChatMessage> messages = new ArrayList<>();
        private final AtomicInteger ids = new AtomicInteger();

        private ChatMessage save(ChatMessage message) {
            if (message.getId() == null) message.setId((long) ids.incrementAndGet());
            if (message.getCreatedAt() == null) message.setCreatedAt(Instant.now().plusNanos(ids.get()));
            messages.add(message);
            return message;
        }

        private List<ChatMessage> latest(Long studentId) {
            return messages.stream().filter(m -> m.getStudent().getId().equals(studentId))
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId).reversed())
                    .limit(12).toList();
        }

        private List<ChatMessage> history(Long studentId) {
            return messages.stream().filter(m -> m.getStudent().getId().equals(studentId))
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId)).toList();
        }

        private void clear(Long studentId) {
            messages.removeIf(m -> m.getStudent().getId().equals(studentId));
        }
    }
}
