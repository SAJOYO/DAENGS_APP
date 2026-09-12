# 출시 빌드의 시설 대화 연결

시설 화면과 공통 채팅은 `DaengsApp.facilityConversation` 한 개를 공유한다. 공통 채팅의
Place 실행은 기존 시설 v2를 사용하고, 결과의 참조를 복구해 같은 조건·카드·선택을 갱신한다.
대응 서버는 [DEV #476](https://github.com/SAJOYO/DAENGS_dev/pull/476)의
`docs/place/assistant-conversation.md` 계약을 따른다.

## 실행 경계

`FacilityAssistant`가 무상태 채팅과 저장 채팅의 공통 전송 함수다. 질문 시점의 계정,
시설 session/revision, 카드 순서, 선택과 찜 명령 순서를 동결한다. 네트워크 오류에는 같은
요청 ID와 문맥을 재사용한다. 새 시설 결과는 recovery가 반환한 참조와 정확히 일치하고
그 사이 계정·검색이 바뀌지 않았을 때만 적용한다.

- 화면을 나가도 시설 검색은 유지한다. 돌아오면 공유 결과를 지도 세션에 연결한다.
- 로그아웃·계정 전환은 공유 검색과 찜 컨트롤러를 초기화한다. 예전 계정의 늦은 응답은 무시한다.
- 서버의 충돌·만료는 최신 상태 또는 기존 조건으로 복구한 뒤 요청을 다시 받는다.
  이전 순번을 새 카드에 자동으로 적용하지 않는다.
- 저장된 대화를 읽을 때는 시설 참조를 실행하지 않는다. 현재 요청의 결과에만
  “현재 시설 보기” 버튼을 붙인다.
- 찜 완료 문구는 공유 `PlaceBookmarkController`의 실제 실행 결과를 기다린다.
  실패·불확실한 저장을 성공으로 말하지 않는다. 저장 채팅의 서버 기록은 준비 시점의
  응답을 보존하므로, 다시 읽은 기록에는 “요청을 준비했어요.”가 남을 수 있다.
- 공통 연결은 일반 시설 검색의 카드·조건을 다룬다. 찜 목록 안의 조건 검색과 그 대화는
  기존 시설 화면에 유지하며 공통 요청에서 `saved_search`를 협상하지 않는다.

사용자에게는 보통 한 문장으로 실행 결과만 말한다. “여기 찜해뒀어요!”,
“조건에 맞는 카페 3곳 찾아뒀어요!”처럼 답한다. 실패·필요한 확인에는 상태를 짧게 덧붙인다.
조건 해석·실행·답변 내용의 소유자는 서버이고, 앱은 원문을 다시 LLM으로 꾸미지 않는다.

## 빌드와 출시 순서

`BuildConfig.FACILITY_CONVERSATION`은 debug/release에서 기본 true다.
debug 비교 빌드만 `-PfacilityConversation=false`로 이전 경로를 선택할 수 있다.

**DEV의 호환 API 배포가 먼저다.** 구버전 서버는 공통 요청의 `facility` 필드를 거절한다.
서버 배포 후 다음 설정과 명령으로 출시 산출물을 만든다. 배포·서명·스토어 업로드는
코드 연결과 별개 단계다.

```properties
# local.properties — 커밋하지 않는다.
daengs.apiBaseUrlRelease=https://daengapi.weareithero.cloud
daengs.gaitUrlRelease=https://daengapi.weareithero.cloud
```

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.assistant.*' --tests 'com.daengs.app.place.*' --tests 'com.daengs.app.ui.places.*' --tests 'com.daengs.app.chat.*' --tests 'com.daengs.app.ui.chat.*'
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

SDK·지도·로그인 키와 업로드 서명 설정은 루트 README를 따른다. 서명 키가 없는 빌드는
컴파일 및 산출물 검증용이며 설치·스토어 업로드용이 아니다. 출시 버전 번호도 별도로 지정한다.

Windows에서 경로에 한글이 있으면 AGP가 거절할 수 있고, 경로 검사를 우회해도 테스트
클래스 로딩이 실패할 수 있다. 이번 검증은 동일 소스를 영문 경로에 복사해 수행했다.
서명 키·외부 로그인 키를 다른 작업 폴더에서 가져오지 않았다.

## 확인 범위

2026-09-12 로컬 검증: 위 패키지의 **435개 테스트 통과, 실패·skip 0**.
`assembleRelease`·`bundleRelease`와 release 필수 lint도 통과했다. 생성된 BuildConfig의
시설 플래그 true 및 HTTPS API 주소를 확인했다. 원 작업 폴더와 영문 빌드 폴더의
Kotlin·Gradle·XML·설정 파일 935개의 SHA-256이 일치했다. APK와 AAB는 서명 전 산출물이다.

`FacilityAssistantTest`는 실제 공유 저장소에 응답을 적용하면서 재시도·수동 검색 우선·계정
전환·잘못된 recovery·실제 찜 완료 대기·만료 복구·이전 명령 재실행 차단을 검사한다.
`AssistantResponseTest`는 구형 후보 카드와 v2 참조, HTTP 200 안의 시설 오류를 구분한다.
위 명령은 기존 시설·찜·채팅 회귀도 함께 실행한다.

운영 Gemini, 실제 회원의 찜 저장, 실기기 지도 이동은 별도 확인이 필요하다.
서버의 모의 통합 테스트와 Android 단위 테스트만으로 해당 동작까지 검증했다고 간주하지 않는다.
