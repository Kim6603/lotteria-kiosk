package kim.kiosk.service;

import kim.kiosk.dto.ChatRequest;
import kim.kiosk.dto.ChatResponse;
import kim.kiosk.service.ctx.ClientPointerContext;
import kim.kiosk.service.ctx.ResponseMetaContext;
import kim.kiosk.service.intent.IntentDetector;
import kim.kiosk.service.intent.LlmIntent;
import kim.kiosk.service.openai.OpenAIChatGateway;
import kim.kiosk.service.openai.OpenAIIntentGateway;
import kim.kiosk.service.ui.UiFlowService;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatDtoUtil chatDtoUtil;

    private final IntentDetector intentDetector;
    private final CartFlowService cartFlowService;
    private final RecommendationService recommendationService;
    private final UiFlowService uiFlowService;
    private final OpenAIChatGateway openAIChatGateway;
    private final OpenAIIntentGateway openAIIntentGateway;

    private final ResponseMetaContext responseMetaCtx;
    private final ClientPointerContext clientPointerCtx;

    @Override
    public ChatResponse getChatResponse(ChatRequest chatRequest) {

        long start = System.currentTimeMillis();

        responseMetaCtx.clear();
        clientPointerCtx.clear();

        String userText = chatDtoUtil.extractUserText(chatRequest);
        String sessionId = chatDtoUtil.extractSessionId(chatRequest);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("[사용자 질문] {}", (userText == null || userText.isBlank()) ? "(empty)" : userText);

        if (userText == null || userText.isBlank()) {
            String a = "질문 내용을 입력해 주세요.";
            ChatResponse r = buildTextOnlyResponse(sessionId, a);

            long backend = System.currentTimeMillis() - start;

            log.info("[AI 답변] {}", a);
            log.info("[백엔드 처리시간(/api/chat)] {} ms", backend);
            log.info("[총 처리시간] {} ms", backend);

            return r;
        }

        final String normalized = chatDtoUtil.normalize(userText);

        LlmIntent llmIntent = null;
        String llmType = "UNKNOWN";
        boolean llmRecommend = false;

        long llmStart = System.currentTimeMillis();
        try {
            llmIntent = openAIIntentGateway.detect(userText);
            if (llmIntent != null && llmIntent.getType() != null) {
                llmType = llmIntent.typeUpper();
            }
        } catch (Exception e) {
            llmType = "UNKNOWN";
        }
        long llmEnd = System.currentTimeMillis();
        log.info("[LLM 인텐트 소요시간] {} ms", (llmEnd - llmStart));

        if ("RECOMMEND".equals(llmType)) {
            llmRecommend = true;
        }

        boolean ruleCartClear = intentDetector.isCartClearIntent(normalized);
        boolean ruleCartRemove = intentDetector.isCartRemoveIntent(normalized);
        boolean ruleCartAdd   = intentDetector.isCartAddIntent(normalized);
        boolean ruleTabSwitch = intentDetector.isTabSwitchIntent(normalized);
        boolean ruleGenericRecommend = isVerySimpleRecommend(normalized);
        boolean ruleRecommendByRule = intentDetector.isMenuRecommendIntent(normalized) || ruleGenericRecommend;

        boolean isCartClear = "CART_CLEAR".equals(llmType) || ruleCartClear;
        boolean isCartRemove = "CART_REMOVE".equals(llmType) || ruleCartRemove;
        boolean isCartAdd = "CART_ADD".equals(llmType) || ruleCartAdd;
        boolean isTabSwitch = "TAB_SWITCH".equals(llmType) || ruleTabSwitch;
        boolean isRecommend = llmRecommend || ruleRecommendByRule;

        boolean isNormalChat = "NORMAL_CHAT".equals(llmType)
                || ("UNKNOWN".equals(llmType)
                && !isCartClear && !isCartRemove && !isCartAdd && !isTabSwitch && !isRecommend);

        String answer;
        try {
            if (isCartClear) {
                answer = cartFlowService.clearCartFlow();
            } else if (isCartRemove) {
                answer = cartFlowService.removeFromCartFlow(sessionId, userText);
            } else if (isCartAdd) {
                answer = cartFlowService.addToCartFlow(sessionId, userText);
            } else if (isTabSwitch) {
                answer = uiFlowService.switchTabFlow(normalized);
            } else if (isRecommend) {
                boolean askAlternative = false;
                if (llmIntent != null && Boolean.TRUE.equals(llmIntent.getAlternative())) {
                    askAlternative = true;
                } else if (intentDetector.isAlternativeFollowup(sessionId, normalized)) {
                    askAlternative = true;
                }
                answer = recommendationService.recommendFromDb(sessionId, userText, askAlternative);
            } else if (isNormalChat) {
                answer = openAIChatGateway.ask(userText);
            } else {
                answer = openAIChatGateway.ask(userText);
            }
        } catch (Exception e) {
            answer = "답변 생성 중 오류가 발생했습니다.";
        }

        log.info("[AI 답변] {}", answer);

        ChatResponse r = buildTextOnlyResponse(sessionId, answer);

        long backend = System.currentTimeMillis() - start;

        log.info("[백엔드 처리시간(/api/chat)] {} ms", backend);
        log.info("[총 처리시간] {} ms", backend);

        return r;
    }

    private boolean isVerySimpleRecommend(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        String n = normalized.replaceAll("\\s+", "");
        if (n.length() > 12) return false;

        if (n.contains("매운") || n.contains("칼칼") || n.contains("달달") || n.contains("저렴")
                || n.contains("싸게") || n.contains("비싼") || n.contains("양많") || n.contains("양많이")) {
            return false;
        }

        return n.contains("메뉴추천")
                || n.contains("추천해줘")
                || n.contains("아무거나추천");
    }

    @SuppressWarnings("unused")
    private boolean isRecommendQueryComplex(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        String n = normalized.replaceAll("\\s+", "");
        return n.contains("매운") || n.contains("칼칼") || n.contains("달달")
                || n.contains("느끼") || n.contains("담백") || n.contains("가벼운")
                || n.contains("든든") || n.contains("저렴") || n.contains("비싼");
    }

    private ChatResponse buildTextOnlyResponse(String sessionId, String answer) {

        ChatResponse r = chatDtoUtil.buildResponseWithMessages(
                sessionId,
                answer,
                null,   // audioDataUrl
                null,   // audioBase64
                responseMetaCtx.snapshot()
        );

        responseMetaCtx.clear();
        clientPointerCtx.clear();
        return r;
    }
}
