package kim.kiosk.dto;

import lombok.Data;

@Data
public class TtsRequest {
    private String text;
    private String sessionId;
}
