package kim.kiosk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TtsResponse {
    private String audioBase64;
    private String audioDataUrl;
    private Long ttsMillis;
}
