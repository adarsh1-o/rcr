package com.example.demo.service;

import com.example.demo.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiResponsesClient implements ChatbotProvider {

    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String SYSTEM_INSTRUCTIONS = "You are RCR Assistant, a general-purpose educational assistant " +
            "for students. Answer the student's actual question, including topics unrelated to this platform. " +
            "Explain difficult ideas in clear, simple language; use examples and step-by-step explanations when useful. " +
            "Explain code and its reasoning. Separate established facts from assumptions, state uncertainty, and ask " +
            "for clarification only when needed. Never invent facts, sources, or citations. When web search is used, " +
            "base current claims on the retrieved sources and identify uncertainty if sources do not establish an answer. " +
            "For questions about the RCR platform, its available features include courses and enrollment, assignments " +
            "and resources, student submissions and grading, Live Classes, quizzes, attendance, course progress, " +
            "doubts and replies, and notifications. Explain only behavior supported by this context; do not invent exact " +
            "menus or policies you have not been told about.";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final boolean webSearchEnabled;

    public OpenAiResponsesClient(RestTemplate chatbotRestTemplate,
                                 @Value("${openai.api.key:}") String apiKey,
                                 @Value("${openai.model:gpt-4.1-mini}") String model,
                                 @Value("${openai.web-search.enabled:true}") boolean webSearchEnabled) {
        this.restTemplate = chatbotRestTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.webSearchEnabled = webSearchEnabled;
    }

    @Override
    public String generateReply(String message, List<ChatMessage> recentMessages, boolean useWebSearch) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatbotProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI assistant is not configured yet. Ask an administrator to set OPENAI_API_KEY and restart the backend.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        String instructions = SYSTEM_INSTRUCTIONS;
        if (useWebSearch && !webSearchEnabled) {
            instructions += " Web search is disabled for this request; clearly say you cannot verify current information and do not present guesses as current facts.";
        }
        body.put("instructions", instructions);
        body.put("input", toInputMessages(recentMessages));
        body.put("max_output_tokens", 450);
        if (useWebSearch && webSearchEnabled) {
            body.put("tools", List.of(Map.of("type", "web_search", "search_context_size", "medium")));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(RESPONSES_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            String answer = extractAnswer(response.getBody());
            if (answer == null || answer.isBlank()) {
                throw new ChatbotProviderException(HttpStatus.BAD_GATEWAY,
                        "The AI provider returned an empty answer. Please try again.");
            }
            return answer.trim();
        } catch (HttpStatusCodeException ex) {
            log.warn("OpenAI Responses request failed with HTTP status {}", ex.getStatusCode().value());
            if (useWebSearch && webSearchEnabled && ex.getStatusCode().value() == 400) {
                throw new ChatbotProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The selected AI model or API account does not support web search. Ask an administrator to choose a compatible OPENAI_MODEL or disable OPENAI_WEB_SEARCH_ENABLED.");
            }
            throw toSafeProviderException(ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("OpenAI Responses request timed out or could not connect");
            throw new ChatbotProviderException(HttpStatus.GATEWAY_TIMEOUT,
                    "The AI assistant took too long to respond. Please try again.");
        } catch (ChatbotProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI Responses returned an unusable response ({})", ex.getClass().getSimpleName());
            throw new ChatbotProviderException(HttpStatus.BAD_GATEWAY,
                    "The AI assistant could not process the response. Please try again.");
        }
    }

    private List<Map<String, Object>> toInputMessages(List<ChatMessage> recentMessages) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatMessage message : recentMessages) {
            String role = "USER".equals(message.getSender()) ? "user" : "assistant";
            input.add(Map.of("role", role, "content", message.getContent()));
        }
        return input;
    }

    private String extractAnswer(Map<?, ?> response) {
        Object outputValue = response == null ? null : response.get("output");
        if (!(outputValue instanceof List<?> output)) {
            return null;
        }
        StringBuilder answer = new StringBuilder();
        for (Object outputItem : output) {
            if (!(outputItem instanceof Map<?, ?> item) ||
                    !"message".equals(item.get("type")) || !"assistant".equals(item.get("role"))) {
                continue;
            }
            Object contentValue = item.get("content");
            if (!(contentValue instanceof List<?> contents)) continue;
            for (Object contentItem : contents) {
                if (!(contentItem instanceof Map<?, ?> content) || !"output_text".equals(content.get("type"))) {
                    continue;
                }
                    if (!answer.isEmpty()) answer.append('\n');
                    Object text = content.get("text");
                    if (text instanceof String outputText) answer.append(outputText);
                    List<String> sourceLinks = new ArrayList<>();
                    Object annotationsValue = content.get("annotations");
                    if (annotationsValue instanceof List<?> annotations) {
                        for (Object annotationItem : annotations) {
                            if (annotationItem instanceof Map<?, ?> annotation &&
                                    "url_citation".equals(annotation.get("type"))) {
                                String title = annotation.get("title") instanceof String value ? value : "Source";
                                String url = annotation.get("url") instanceof String value ? value : "";
                                if (!url.isBlank()) sourceLinks.add("[" + title + "](" + url + ")");
                            }
                        }
                    }
                    if (!sourceLinks.isEmpty()) {
                        answer.append("\nSources: ").append(String.join(", ", sourceLinks));
                    }
            }
        }
        return answer.toString();
    }

    private ChatbotProviderException toSafeProviderException(int status) {
        if (status == 401 || status == 403) {
            return new ChatbotProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider rejected its credentials. Ask an administrator to check OPENAI_API_KEY.");
        }
        if (status == 404) {
            return new ChatbotProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured AI model is unavailable to this API account. Ask an administrator to check OPENAI_MODEL.");
        }
        if (status == 429) {
            return new ChatbotProviderException(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI provider is rate limited or its API usage limit has been reached. Please try again later.");
        }
        if (status >= 500) {
            return new ChatbotProviderException(HttpStatus.BAD_GATEWAY,
                    "The AI provider is temporarily unavailable. Please try again later.");
        }
        return new ChatbotProviderException(HttpStatus.BAD_GATEWAY,
                "The AI provider could not process this request. Check the chatbot configuration and try again.");
    }
}
