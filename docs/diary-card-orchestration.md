# 독립 카드 작성 결과 연결

DEV의 기존 산책 일기 응답 `walk-diary-board-v1`에 선택 필드 `scenes[].writing`을 추가했다. [DEV 구조와 Mermaid](https://github.com/SAJOYO/DAENGS_dev/blob/fix/diary-shared-orchestration/docs/walk/card-orchestration.md)를 기준으로 한다.

```mermaid
flowchart LR
    A[WalkDiarySync: 기존 일기 API] --> B[ServerDiaryBoard]
    B --> C[카드 title·조립 body·SGIS dong]
    B --> D[PublishedCardWriting: 독립 부분과 내용 버전 검사]
    C --> E[기존 Room 저장]
    D --> E
    E --> F[WalkDiaryReader]
    F --> G[기존 목록·카드·편집기]
```

공간·조건부 행동 작성 후 DEV가 채택 본문을 고정하고 제목만 별도 작업으로 생성한다. APP는 서버의 카드 `title`을 목록과 상세에서 사용한다. `킁킁` 같은 행동 종류가 카드 제목을 대신하지 않는다. 전체 산책 제목은 이 변경의 대상이 아니다.

DEV의 실행은 기존 `orchestration` 안의 일기 LangGraph로 연결되어 있고, 어시스턴트와 같은 `JobExecutor`를 사용한다. APP의 요청·읽기·Room 저장 경로는 그대로다. 본문·위치는 제목 전에 고정되며, 제목 묶음 일부가 실패해도 성공한 카드 제목과 본문을 유지한다. 제목의 내용 버전에는 별도 보존 원문이 들어가지 않지만, 원문 변경은 서버의 기존 발행 버전 검사와 APP의 사용자 편집 보존 대상이다.

SGIS 정규화의 `sido / sigungu / dong` 중 기존 표시 계약대로 **`dong`만** 시간 옆 위치값에 사용한다. 제목의 표현과 위치 표시가 서로 의존하지 않는다.

`PublishedCardWriting`은 제목이 참조한 내용 버전, 공간/행동 부분과 최종 본문의 일치, 행동핀의 행위자 연결, 원문 보존을 검사한다. 이를 내부 발행 근거로 저장하며 읽기 화면을 별도 칸으로 나누지 않는다. 기존 JSON에 이 필드가 없어도 이전 방식으로 읽는다.

사용자가 수정한 제목·본문은 기존 `StoryboardDraft`가 우선한다. 분리 작성 원본은 과거 공개 근거일 뿐, 사용자 편집 본문을 다시 합성하거나 덮어쓰는 자료가 아니다. API 응답은 기존 `sourcePayload`와 Room 저장 경로를 거치므로 재개방 후에도 그대로 복원된다.

`diary-card-orchestration-v1.json`은 DEV의 실제 HTTP 생성 테스트에서 외부 SGIS·EGIS·LLM만 대체한 응답이다. `PublishedCardWritingTest`와 `WalkDiarySpacePersistenceTest`가 이 응답의 제목·행동 유무·행정동 표시·원문·편집·Room 재개방을 확인한다. 실제 모델 문장 품질을 입증하는 표본은 아니다.

## 관측 설명 보존 — APP #395 · DEV #503

새 관측 카드의 `writing.observation`은 기기 동선의 모임·상대 저속·상대 고속을 설명하는
확정 문장이다. 수신 시 관측 종류·기기 주체·행동 미추론·코어 참조/버전과 본문 조립을 검사한다.
서버가 보낸 관측 설명 → 공간 본문을 기존 한 본문으로 읽고 새 UI나 진단 표시를 추가하지 않는다.
제목은 서버가 채택한 관측 설명까지 읽어서 만든다.

과거 응답에서 이 필드가 없으면 그대로 읽는다. 앱이 관측 문장을 임의로 보충하거나
사용자 편집 뒤 발행 부분을 다시 합성하지 않는다. 빈 본문으로 편집한 경우도 유지한다.
기존 `sourcePayload`와 Room 저장을 사용하므로 DB 마이그레이션은 없다.

[DEV #503](https://github.com/SAJOYO/DAENGS_dev/pull/503)의
`test_http_observation_meaning_is_published_once_and_exports_app_contract`에서 나온 응답을
`diary-observation-card-v1.json`으로 저장했다. 서버의 `backend/evals/walk-diary/observation-card-v1.json`과
SHA-256은 `ed1380641b228a78304889fc11bc491837d79be42155184a1c4b75be45126ffd`로 같다.
모델·외부 공급자·서버 DB는 대역이고, 앱 Room 재개방 테스트는 로컬 테스트 DB를 사용한다.
이 표본은 실제 Gemini 품질이나 실기기 실행 증거가 아니다.

**적용 순서:** 이 앱의 수신 지원을 사용자 기기에 먼저 반영한 뒤 DEV에서 새 관측 본문을
생성한다. 이전 앱은 새 본문을 기존 부분만으로 재조립해 검사하므로 새 응답을 거절한다.
현재 PR에서는 서버 머지·배포나 앱 실기기 설치를 수행하지 않았다.
