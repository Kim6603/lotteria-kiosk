package kim.kiosk.controller;

import kim.kiosk.dto.ChatRequest;
import kim.kiosk.dto.ChatResponse;
import kim.kiosk.service.ChatService; // 아직 만들지 않았지만 곧 만들 파일입니다.
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest chatRequest) {
        return chatService.getChatResponse(chatRequest);
    }
}