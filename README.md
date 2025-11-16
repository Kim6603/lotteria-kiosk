# AI 기반 롯데리아 키오스크  
Spring Boot + React + OpenAI + Google STT/TTS

고객이 음성으로 주문하고, AI가 메뉴 추천, 대화형 상담, 카테고리 이동, 장바구니 조작 등을 도와주는 음성 기반 AI 키오스크 시스템 입니다.

프론트는 React(Vite), 백엔드는 Spring Boot + MySQL + MyBatis 기반으로 구성되어 있습니다.

---

## 주요 기능

### 1. 음성 기반 AI 상담
- 마이크 버튼 한 번으로 음성 인식(STT) 활성화  
- 사용자의 음성을 실시간으로 텍스트 변환  
- 변환된 텍스트를 기반으로 OpenAI가 응답 생성  
- 응답은 Google TTS로 합성하여 자동 재생

### 2. AI 메뉴 추천 엔진
- 사용자의 질문을 Intent 분석  
- 예: “치킨 추천해줘”, “가벼운 메뉴 없어?”, “매운 거 뭐 있어?”  
- 기준에 맞춰 단일 메뉴를 정확히 추천 
- 추천 시 UI는 해당 메뉴만 표시
- 카테고리 자동 비활성화 + 자동 이동

### 3. 장바구니 제어
- AI가 장바구니에 메뉴 추가/삭제 가능  
- 예:  
  - “사이다 하나 추가해줘”  
  - “방금 추천한 메뉴 빼줘”  
  - “장바구니 비워줘”

### 4. 직관적인 React 기반 키오스크 UI
- 9:16 비율의 터치형 키오스크 UI  
- 카테고리 탭, 상품 리스트, 상세 미리보기  
- 주문 내역 확인 및 결제 버튼

---

## 기술 스택

### Backend (Spring Boot)
- Spring Boot 3.5.x
- MyBatis
- MySQL 8.x
- OpenAI GPT-4o-mini API
- Google STT / TTS(Text-to-Speech)
- Lombok

### Frontend (React)
- React 18 (Vite 기반)
- React Router
- CSS 커스텀 UI

---

## 실행 방법

### Backend 실행 (Spring Boot)

1. `kiosk_back` 폴더에 `.env` 파일 생성  
2. IntelliJ → EnvFile 플러그인 설치  
3. `.env` 연결
4. Spring Boot Main 실행

### Frontend 실행 (React)

cd kiosk_front
npm install
npm run dev
