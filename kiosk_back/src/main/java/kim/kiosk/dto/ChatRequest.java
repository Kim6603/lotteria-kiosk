package kim.kiosk.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    private String message;
    private String language;
    private String sessionId;
    private Long sttMillis;
}