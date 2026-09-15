# 관계 일기 APP 연결

서버 계약: SAJOYO/DAENGS_dev #541, `574daf10`. APP 작업: #422.

## 1단계: 수신과 저장

`walk/diary/relational/RelationalDiary.kt`가 공개 응답의 모델이고
`RelationalDiaryParser.kt`가 `walk-relational-diary-response-v1`을 읽는다.
기존 `GeoStoryboardBundle`이나 구형 일기 문장 조립으로 변환하지 않는다.

- 공간·행동의 `returned / failed / not_requested`와 검수 상태를 각각 보존한다.
- 본문은 서버의 `body` 그대로다. 빈 본문도 장면이며, 실패한 부분을 기본 문장으로 채우지 않는다.
- 헤더의 동·날씨는 본문과 분리한다. 기존 구·동 카드 UI에 연결하는 작업은 3단계다.
  현재 서버 헤더에는 `dong`만 있으며, 구 정보는 새로 만들어내지 않는다.
- 현재 공간의 도로명·피복·주변 대상·조회 영역 근거와 수집 상태, 비교 장면 ID를 보존한다.
- 시각·좌표·위치 방식·정확도·원본 GPS 참조를 함께 읽는다. 같은 시각의 장면 순서는 서버 순서다.
- 메모의 공백/줄바꿈, 사진 참조, 기록의 버전·위치·삭제 상태를 별도로 보존한다.
- 날씨와 공간 근거의 가변 값은 JSON 객체로 유지한다. 자료 없음과 0을 바꾸지 않는다.

저장은 기존 Room `walk_scene_analysis.bundle` 칸에 전용 캐시 형식을 쓴다.
새 테이블·마이그레이션은 없다. 최신 응답과 마지막 발행 응답을 별도로 저장하며
각 응답의 원문 문자열을 보존한다. 실패한 갱신은 같은 입력의 이전 발행본을 유지할 수 있고,
`stale`이거나 입력이 달라졌으면 이전 발행본을 제공하지 않는다.

DAO 진입점:

1. 요청 직전 `relationalDiaryInputStamp(sessionId)`로 현재 원본 표식을 얻는다.
2. 응답 수신 시 `acceptRelationalDiary(raw, sessionId, walkId, ownerId, expectedStamp)`.
3. 저장본 열람 시 `readRelationalDiary(sessionId, ownerId)`.

각 저장·읽기는 Room 트랜잭션에서 소유자·완료 산책·현재 기록/사진 버전을 확인한다.
저장 시 요청했던 서버 산책 ID와도 대조한다. 새 세대가 먼저 들어오면 늦은 이전 세대는 거절한다.
`diary:relational:` 표식으로 구형 저장 함수의 덮어쓰기와 기본 일기 발행을 차단한다.

## 재현 입력과 검사

`app/src/test/resources/storyboard/relational-diary-v1.json`은 DEV의 실제 HTTP·Postgres·Gemini
실행 결과에서 **공개 response만** 추출한 것이다. 합성 산책 3장면이고 실제 사용자의 기록은 아니다.
검수에서 통과한 문장에도 알려진 의미 오류가 있다. 파서가 보존했다는 것이 문장 품질 합격은 아니다.

대상 코드 검사:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.relational.RelationalDiaryParserTest' --tests 'com.daengs.app.walk.store.RelationalDiaryStorageTest' --tests 'com.daengs.app.walk.sync.WalkDiarySyncTest'
```

원본 응답·부분 실패, 메모/사진 확장 입력, 실제 Room 종료 후 재개방, 원본 수정과 늦은 응답 차단을 확인한다.
새 LLM 호출은 없다.

2026-09-15 결과: 새 파서 5개·저장 6개 통과, 기존 동기화 10개 통과.
파일 DB 재개방 검사는 Windows에서 긴 임시 경로로 처음 실패했으며, 검사 이름과 DB 파일명을
줄인 뒤 파일 DB를 실제로 닫고 다시 여는 경로가 통과했다. Kotlin 컴파일과 diff 검사 통과.

## 다음 연결

- 2단계: capability 선택·생성 POST·예약 대기·GET 복구. 새 형식에서 기존 20초 기본 발행 우회.
- 3단계: 전용 읽기 모델을 기존 지도/일기 UI에 연결. 기존 구·동 표시와 서랍 동작 재사용.
- 현재 화면/자동 동기화는 새 DAO를 아직 호출하지 않는다. 이 단계만으로 새 일기가 화면에 표시되지는 않는다.
- 새 응답의 행동 문장에는 현재 핀의 원본 ID가 별도 필드로 제공되지 않고, `originals`는 주로 메모/사진이다.
  `scene_id`를 앱의 entry ID로 간주하거나 임의로 핀을 연결하면 안 된다. 행동 편집/삭제 연결 시 서버 참조 계약을 확인한다.
