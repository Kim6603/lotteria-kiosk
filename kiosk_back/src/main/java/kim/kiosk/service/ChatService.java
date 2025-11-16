package kim.kiosk.service;

import kim.kiosk.dto.ChatRequest;
import kim.kiosk.dto.ChatResponse;

public interface ChatService {
    ChatResponse getChatResponse(ChatRequest chatRequest);
}