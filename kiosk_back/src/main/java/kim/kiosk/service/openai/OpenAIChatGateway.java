package kim.kiosk.service.openai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIChatGateway {

    private final OpenAiService openAiService;
    private final ChatDtoUtil chatDtoUtil;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-tokens:0}")
    private Integer maxTokens;

    public String ask(String userText) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "You are a polite assistant for a Lotteria (롯데리아) kiosk in Korea. Keep responses concise (1-2 sentences) in Korean."));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userText));

            ChatCompletionRequest.ChatCompletionRequestBuilder b = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages);
            if (maxTokens != null && maxTokens > 0) b.maxTokens(maxTokens);

            ChatCompletionResult result = openAiService.createChatCompletion(b.build());
            String answer = null;
            if (result != null && result.getChoices() != null && !result.getChoices().isEmpty()) {
                var msg = result.getChoices().get(0).getMessage();
                if (msg != null) answer = msg.getContent();
            }
            if (answer == null || answer.isBlank()) answer = "무엇을 도와드릴까요?";
            return answer.trim();
        } catch (Exception e) {
            return "답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
        }
    }
}
