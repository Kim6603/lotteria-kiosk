package kim.kiosk.service.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import kim.kiosk.service.intent.LlmIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIIntentGateway {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.intent-max-tokens:16}")
    private Integer intentMaxTokens;

    public LlmIntent detect(String userText) {
        try {
            String input = (userText == null) ? "" : userText.trim();
            if (input.isEmpty()) {
                return defaultUnknown();
            }

            final int MAX_INPUT_LEN = 80;
            if (input.length() > MAX_INPUT_LEN) {
                input = input.substring(0, MAX_INPUT_LEN);
            }

            List<ChatMessage> messages = new ArrayList<>();

            String systemPrompt =
                    "역할: 롯데리아 키오스크 한국어 발화를 의도별로 분류하는 분류기.\n" +
                            "사용자 문장을 보고 아래 레이블 중 하나만 출력하라.\n" +
                            "RECOMMEND, CART_ADD, CART_REMOVE, CART_CLEAR, TAB_SWITCH, NORMAL_CHAT, UNKNOWN\n" +
                            "이전 추천과 다른 추천을 요구하면 RECOMMEND_ALT 를 출력하라.\n" +
                            "설명, 따옴표, 코드블록 없이 이 단어만 출력.";

            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), input));

            int maxTokens = 4;
            if (intentMaxTokens != null && intentMaxTokens > 0) {
                maxTokens = Math.min(intentMaxTokens, 4);
            }

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.0)
                    .topP(1.0)
                    .n(1)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);

            if (result == null
                    || result.getChoices() == null
                    || result.getChoices().isEmpty()
                    || result.getChoices().get(0).getMessage() == null) {
                return defaultUnknown();
            }

            String content = result.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                return defaultUnknown();
            }

            String token = content.trim().split("\\s+")[0];
            if (token.isEmpty()) {
                return defaultUnknown();
            }

            String upper = token.toUpperCase();
            boolean alternative = false;
            if (upper.endsWith("_ALT")) {
                alternative = true;
                upper = upper.substring(0, upper.length() - "_ALT".length());
            }

            String type;
            switch (upper) {
                case "RECOMMEND":
                case "CART_ADD":
                case "CART_REMOVE":
                case "CART_CLEAR":
                case "TAB_SWITCH":
                case "NORMAL_CHAT":
                case "UNKNOWN":
                    type = upper;
                    break;
                default:
                    type = "UNKNOWN";
                    alternative = false;
                    break;
            }

            LlmIntent intent = new LlmIntent();
            intent.setType(type);
            intent.setAlternative(alternative);
            intent.setMenuName(null);
            intent.setCategory(null);
            intent.setQuantity(null);

            return intent;
        } catch (Exception e) {
            log.warn("LLM intent detection failed: {}", e.getMessage());
            return defaultUnknown();
        }
    }

    private LlmIntent defaultUnknown() {
        LlmIntent i = new LlmIntent();
        i.setType("UNKNOWN");
        i.setAlternative(false);
        i.setQuantity(null);
        i.setMenuName(null);
        i.setCategory(null);
        return i;
    }
}
