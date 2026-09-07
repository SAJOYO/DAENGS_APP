# 목록에서 여는 산책 지도 일기

## 제품 구조

지난 산책의 페이지 목록에서 한 세션을 선택하면 지도 일기로 들어간다. 별도 지도 탭은 없다.
한 세션 안에서 속도 동선·머문 자리·사용자 액션과 시간순 장면을 확인한다. 지도 숫자는
장면 순서다. 같은 위치의 여러 장면은 `2 · 3`처럼 번호를 나열하며 밀집 개수를 쓰지 않는다.
위치 없는 장면도 순서에 포함하고 목록에서 읽는다. 자동 장면은 v4에 담긴 원본 관측 지점을
이 기기의 GPS 기록과 대조해 배치한다. 경로 거리·가까운 시각으로 다른 점을 대신 선택하지 않는다.

단일 세션은 기록이 하나뿐이어도 볼거리가 있어야 한다. 속도와 체류는 관측 표현이고,
사용자가 남기는 액션이 해당 세션의 의미를 채운다. 머문 자리를 자동 행동/스토리로 만들지 않는다.

후속 셀로판은 계절·날씨 조건에 맞는 세션과 스토리보드를 모아보는 수단이다. 누적 브러시는
그 집합의 공간적 빈도만 표현한다. 익숙함/새로움이나 특별함을 자동 판단하지 않는다.

## 목록과 썸네일

- 한 페이지 5개. 이전/다음과 페이지 번호를 표시한다. 상세에서 돌아오면 페이지와 스크롤을 유지한다.
- Room은 소유 계정·종료 여부·강아지 조건을 먼저 적용하고 `(startedAtMillis DESC, id DESC)`
  커서로 조회한다. 앞에 새 산책이 들어와도 다음 페이지에 같은 기록을 반복하지 않는다.
- `WalkHistory.finishedPage`는 기존 산책 인정/기록 보존 규칙을 적용하면서 다음 페이지 존재 여부를
  확인할 한 건까지 읽는다. 짧아서 제외될 기록은 건너뛴다. 모든 과거 GPS를 한 번에 조회하지 않는다.
- 페이지에 필요한 후보의 전체 표시 동선을 기존 `TrailRecorder`로 복원하고 썸네일용 사본을 축약한다.
  기존 5,000점 꼬리만 사용하지 않는다. 각 구간 시작·끝·극값을 보존하는 반복형 RDP로 꺾임을 남긴다.
- 거리/시간과 원본은 바꾸지 않는다. 일시정지·GPS 단절 구간을 이어 붙이지 않는다.
- 썸네일은 Canvas로 그린 동선 + 출발·도착이다. SDK 지도나 타일 요청을 행마다 만들지 않는다.
  12m 이내 시작/종료는 합쳐 표시한다. 상세의 장면 번호는 작은 썸네일에는 넣지 않는다.
- 제목은 저장된 스토리보드 대표 제목을 사용하고, 없으면 날짜 기반 산책 제목을 사용한다.
  생성 제목 아래에도 산책 날짜를 표시한다.

## 대표 제목과 장면 생성

서버 계약은 [DAENGS_dev#308](https://github.com/SAJOYO/DAENGS_dev/pull/308)이다.
관측 사실로 장면 후보를 만든 뒤, 한 LLM 호출에서 대표 제목과 장면 제목을 함께 받아 저장한다.
앱은 v3의 `title`과 `title_fact_ids`를 검증하고 장면 bundle과 같은 Room 행에 보관한다.
목록·지도·검토 화면은 같은 제목을 읽으며, 검토 스냅샷에도 대표 제목을 포함한다.

- 한 페이지의 제목만 관측한다. 제목 조회는 GPS 복원·사진 조회·네트워크 생성을 호출하지 않는다.
- 사용자 액션이 수정/삭제되거나 미동기화 상태면 이전 제목도 이전 장면과 함께 숨긴다.
  같은 원본에 대한 재시도 실패는 저장된 장면·제목을 유지한다.
- 키 미설정·생성 실패·시간 초과·근거 계약 오류는 제목 없는 사실 장면으로 저장된다.
  날짜 제목을 보여주며 목록 진입 때 재생성하지 않는다. 명시적 장면 재분석으로 다시 생성한다.
- v1/v2 캐시도 읽는다. 구서버가 v3 형식만 422로 거절하면 v2로 한 번 재요청한다.
  다른 422·네트워크 오류에 형식을 바꾸어 재요청하지 않는다.
- 제목은 짧은 사실 요약이다. 감정·의도·익숙함·방문 여부를 추론하는 정책은 도입하지 않는다.
  사실 ID 검증은 의미 정확성까지 보장하지 않으므로 실제 생성 품질 검토는 별도다.

## 단일 지도와 기록

자동 장면 위치의 서버 계약은 [DAENGS_dev#313](https://github.com/SAJOYO/DAENGS_dev/pull/313)이다.
`scene.observation`의 원본 순번·chain·시각을 선택한 세션 GPS와 대조하고 좌표 차이 1m 이내만
허용한다. `WalkSessionDetail`에 이미 조회한 원본을 실어 보내므로 목록에 GPS 조회를 추가하지 않는다.
출발/도착은 서버의 첫/마지막 유효 관측 지점이며 관측 공백은 위치 없는 카드다.
원본 누락/불일치는 같은 장소의 다른 기록이나 거리 보간으로 대신하지 않는다.
표시 선에서 생략한 원본 점은 확인된 원본 좌표 자체로 표시하므로, 필터 차이에 따라 선과
조금 떨어질 수 있다. 액션의 위치/시각으로 선택한 자동 환경 장면은 GPS 식별자를 빌리지 않는다.
번호 핀은 출발/도착 아이콘보다 앞에 표시해 클릭을 가리지 않으며, 카드 이동과 핀 선택을 공유한다.
구버전 캐시는 그대로 읽는다. 자동 위치 정보가 없는 기존 일기는 명시적 장면 재분석으로 갱신한다.
형식 enum 거절에만 v4 → v3 → v2로 재요청한다.

- `WalkHistory.sessionDetail`의 전체 경로·속도 시각·체류 결과를 `composeMapScene(WALK, ...)`에 전달한다.
  App #187/#188의 색상 설정, 속도 표현, 머문 자리 계산과 배치를 그대로 재사용한다.
- `WalkDiaryReader`는 선택한 세션 하나의 Room 기록·사진·분석·검토본을 관측한다.
  계정/삭제/종료 여부, 사용자 수정·숨김·원본 변경 재검토 정책을 유지한다.
- 장면 번호를 누르거나 목록에서 고르면 해당 카드가 열린다. 이전/다음으로 같은 세션의 장면을 이동한다.
- `＋ 기록`으로 동선에서 지점을 고른다. 기존 경로 선택기가 반환한 실제 저장 정점의 위치·시각·정확도로
  기존 `WalkEntryEditor`를 연다. 지도에서 누른 임의 좌표나 현재 GPS를 과거 사실로 저장하지 않는다.
  선택 시각을 먼저 보여주며 같은 장소에 재방문한 경우 관측 시각을 고를 수 있다.
- 경로가 없으면 위치 없는 메모를 작성할 수 있다. 편집·삭제와 동기화 큐는 기존 `WalkEntryStore`를 쓴다.
- 원본 entry 식별자로 편집한다. 사진과 자동 생성 장면을 행동 원본으로 잘못 편집하지 않는다.
- 스토리보드 검토는 기존 화면에 연결한다. Geo 실험 페이지를 앱에 넣지 않는다.

## 구현 경계와 검증

main 소스셋 구현이며 debug/release가 같은 제품 흐름을 사용한다. 기존 dev의 속도/체류를 병합했다.
새 DB 테이블·마이그레이션·의존성은 없다. 서버 배포나 dev 머지는 수행하지 않는다.

대상: 실제 Room의 페이지 경계·소유 계정·짧은 기록 보존, 6,001점 동선의 출발/구간 보존,
번호와 원본 entry 연결, 작은 화면 카드 배치, 기존 읽기/편집/속도/체류 표시 경계.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*WalkHistoryTest' --tests '*WalkHistoryThumbnailTest' --tests '*WalkDiaryTest' --tests '*WalkDiaryReaderTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkEntryEditorTest' --tests '*StayStampMarkerTest' --tests '*WalkStylePolicyTest' :app:assembleDebug
```

전체 테스트는 실행하지 않는다. Compose 이미지의 지도 자리표시는 실제 Naver SDK 검증이 아니다.
GPS 현장 수신·체류 문턱값·배터리 검증은 이번 화면 작업과 별개다.

2026-09-07 실행 결과: 위 대상 37개 통과(실패/skip 0), Debug 빌드 성공.
픽셀 에뮬레이터에 `adb install -r` Success 및 MainActivity 실행을 확인했다. 설치된 APK 전체와
빌드 출력의 SHA-256, 설치된 머문 자리 WebP와 저장소 에셋의 SHA-256이 일치했다.
현재 둘러보기 계정에 저장 산책이 없어 실제 세션의 Naver 지도·액션 저장 조작은 미검증이다.
작은 화면의 긴 카드와 이동 버튼은 `app/build/outputs/walk-diary-map-card.png`로 확인했다.

## 후속

조건별 세션 검색, 관측 완전성 분모와
계산 대상 식별자를 가진 누적 브러시 계약은 후속 작업이다. 단순 목록 페이지를 누적 집계의
모집단으로 사용하지 않는다. 선택한 일기를 바꿔도 적용한 배경 조건은 유지한다.

### 대표 제목 연결 검증 (2026-09-07)

제목·근거 파서, 스토리보드 스냅샷, 실제 Room 저장/읽기, 이전 서버 형식 재시도,
목록 제목 변경과 지도 카드/검토 화면 경계를 선택했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*WalkDiaryTitleTest' --tests '*GeoStoryboardBundleTest' --tests '*StoryboardEvidenceTest' --tests '*WalkStoryboardTest' --tests '*WalkStoryboardSyncTest' --tests '*WalkDiaryReaderTest' --tests '*WalkDiaryTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkStoryboardScreenTest' :app:assembleDebug
```

44개 통과(실패/오류/skip 0), Debug 빌드 성공. 픽셀 설치 Success와 MainActivity 실행을 확인했다.
서버 배포 및 실제 계정/LLM 생성 확인은 수행하지 않았다. 테스트의 제목은 명시적인 fixture다.

### 자동 장면 위치 연결 검증 (2026-09-07)

원본 GPS와 v4 장면 연결, 실제 Room 저장/읽기, 구버전 호환, 핀 번호와 카드 선택을 대상으로 실행했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*WalkSceneAnchoringTest' --tests '*WalkStoryboardSyncTest' --tests '*GeoStoryboardBundleTest' --tests '*StoryboardEvidenceTest' --tests '*WalkDiaryReaderTest' --tests '*WalkDiaryTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkHistoryTest' --tests '*WalkStoryboardTest' --tests '*WalkDiaryTitleTest' :app:assembleDebug
```

59개 통과(실패/오류/skip 0), Debug 빌드 성공. 서버 테스트와 같은 합성 왕복/일시정지/GPS 공백 자료에서
장면 7개 중 6개 위치가 연결되고 공백 장면은 카드로 남는다. UI 테스트의 지도 표면은 대역이다.
픽셀 에뮬레이터 설치 Success, MainActivity 실행 Status: ok, 설치 APK와 빌드 파일의 SHA-256 일치를 확인했다.
사용자 저장소에 합성 기록을 넣지 않았다. 실제 계정의 v4 재분석과 NAVER 지도 위 핀 조작은 미검증이다.
