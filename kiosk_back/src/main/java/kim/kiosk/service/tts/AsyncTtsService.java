package kim.kiosk.service.tts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTtsService {

    private final TtsService ttsService;

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public String enqueue(String sessionId, String answer) {
        String jobId = UUID.randomUUID().toString();

        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                byte[] audio = ttsService.synthesize(answer);
                if (audio != null && audio.length > 0) {
                    store.put(jobId, audio);
                    long end = System.currentTimeMillis();
                    log.info("[TTS-ASYNC] jobId={} sessionId={} TTS 소요시간={} ms, size={} bytes",
                            jobId, sessionId, (end - start), audio.length);
                } else {
                    long end = System.currentTimeMillis();
                    log.warn("[TTS-ASYNC] jobId={} sessionId={} TTS 결과 없음 ({} ms)", jobId, sessionId, (end - start));
                }
            } catch (Exception e) {
                long end = System.currentTimeMillis();
                log.warn("[TTS-ASYNC] jobId={} sessionId={} TTS 실패 ({} ms): {}",
                        jobId, sessionId, (end - start), e.getMessage());
            }
        });

        return jobId;
    }

    public byte[] consume(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        return store.remove(jobId);
    }
}
