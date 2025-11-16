package kim.kiosk.service.tts;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TtsService {

    private final TextToSpeechClient textToSpeechClient;

    @Value("${tts.language:ko-KR}")
    private String ttsLanguage;

    @Value("${tts.voice:ko-KR-Standard-A}")
    private String ttsVoice;

    @Value("${tts.encoding:MP3}")
    private String ttsEncoding;

    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) return null;
        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(ttsLanguage)
                .setName(ttsVoice)
                .build();

        AudioEncoding encoding = switch (ttsEncoding.toUpperCase()) {
            case "MP3" -> AudioEncoding.MP3;
            case "OGG_OPUS" -> AudioEncoding.OGG_OPUS;
            case "LINEAR16" -> AudioEncoding.LINEAR16;
            default -> AudioEncoding.MP3;
        };

        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(encoding)
                .build();

        SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
        ByteString audio = response.getAudioContent();
        return audio != null ? audio.toByteArray() : null;
    }
}
