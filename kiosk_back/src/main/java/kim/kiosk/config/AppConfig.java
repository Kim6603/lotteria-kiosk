package kim.kiosk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${openai.api-key}")
    private String openaiApiKey;

    // Google Cloud 인증 키 파일
    @Value("${spring.cloud.gcp.credentials.location}")
    private Resource gcpCredentials;

    // OpenAI API 설정
    // Duration.ofSeconds는 최대 요청 시간 (30초)
    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(openaiApiKey, Duration.ofSeconds(30));
    }

    // Google Cloud Text-to-Speech 설정
    @Bean
    public TextToSpeechClient textToSpeechClient() throws IOException {
        try (InputStream credentialsStream = gcpCredentials.getInputStream()) {         // JSON 인증 파일 읽기
            CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(  // API에 요청 보낼 때 인증 정보를 얻는 인터페이스
                    ServiceAccountCredentials.fromStream(credentialsStream)             // 인증 JSON 파일을 객체로 변환. JSON 안에는 프로젝트 ID, 인증키, 클라이언트 이메일, 인증 메타 데이터 등이 들어있음
            );

            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()           // 인증 객체 생성
                    .setCredentialsProvider(credentialsProvider)
                    .build();

            return TextToSpeechClient.create(settings);                                 // 실제 클라이언트 생성
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
