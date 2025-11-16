package kim.kiosk.controller;

import kim.kiosk.dto.TtsRequest;
import kim.kiosk.dto.TtsResponse;
import kim.kiosk.service.tts.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tts")
public class TtsController {

    private final TtsService ttsService;

    @PostMapping
    public TtsResponse synthesize(@RequestBody TtsRequest request) {
        long start = System.currentTimeMillis();

        String text = Optional.ofNullable(request.getText())
                .orElse("")
                .trim();

        if (text.isEmpty()) {
            return new TtsResponse(null, null, 0L);
        }

        byte[] audio = ttsService.synthesize(text);
        String base64 = (audio != null && audio.length > 0)
                ? Base64.getEncoder().encodeToString(audio)
                : null;

        String dataUrl = (base64 != null)
                ? "data:audio/mp3;base64," + base64
                : null;

        long took = System.currentTimeMillis() - start;

        log.info("[TTS 소요시간] {} ms (len={})", took, text.length());

        return new TtsResponse(base64, dataUrl, took);
    }
}
