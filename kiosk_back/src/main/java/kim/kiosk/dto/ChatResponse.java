package kim.kiosk.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ChatResponse {
    private List<MessageData> messages;
    private String sessionId;
}