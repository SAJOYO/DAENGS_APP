# 행동 핀 앱 연결 — 4단위

구현: [APP #232](https://github.com/SAJOYO/DAENGS_APP/pull/232).
추정기: [#228](https://github.com/SAJOYO/DAENGS_APP/pull/228).
서버: [DEV #357](https://github.com/SAJOYO/DAENGS_dev/pull/357).

## v2 전환과 배포 조건

APP #237의 임시 v1 생성 스위치와 동기화 우회를 제거했다. 새 행동은 아래 v2 흐름으로
저장하며, 기존 v1 행동·메모는 저장된 형식 그대로 전송·조회한다. 과거 기록을 일괄
v2로 바꾸거나 원본 GPS 참조를 새로 만들지 않는다. 추가 Room 마이그레이션은 없다.

서버 계약은 DAENGS_dev `85ff1741` 기준이다. 배포 환경에는 v2용 DB 스키마와 함께
`DAENGS_WALK_ENTRY_V2_ENABLED=true`, `DAENGS_WALK_ENTRY_V2_WRITE_ENABLED=true`가 필요하다.
인증된 `GET /app/walks/entry-capabilities`의 `read_versions`·`write_versions`에
`walk-entry-v2`, `active_policy_versions`에 `action-pin-policy-v1`이 있어야 신규 전송한다.
일시정지·종료 시각을 정확히 전송하려면 `pin_observation_cutoff_supported=true`도 필요하다.
장면 생성은 별도로 `storyboard_formats`의 v5 지원을 확인한다.

생성·내용 정정은 `PUT /app/v2/walks/{walk_id}/entries/{entry_id}`, 이후 위치 확정은
동일 경로의 `/pin`으로 보낸다. capabilities와 산책 원본 업로드 경로는 `/app/walks`를 유지한다.
서버가 아직 준비되지 않았거나 신규 쓰기를 중단하면 새 행동을 기기에 보관하고 전송을 재시도한다.
v2가 섞인 산책은 v2 지원을 기다리고, v1만 있는 산책은 구서버에서도 기존 전송을 계속한다.
코드 병합만으로 실제 배포·플래그 활성화를 확인한 것으로 간주하지 않는다.

서버 [DEV #441](https://github.com/SAJOYO/DAENGS_dev/pull/441)의
[GPS 기록 구분 계약](https://github.com/SAJOYO/DAENGS_dev/blob/fix/gps-recording-evidence/docs/walk/gps-recording-contract.md)을 연결했다.
앱은 기존 recordingEligible을 원본 업로드에 포함하고 상세 응답에서도 복원한다.
알려진 구분이 있는 원본은 업로드 전에 gps-recording-v1 지원을 확인한다.
RAW_UPLOADED/DERIVED 재시도와 v2 핀 전송 전에도 실제 저장 receipt를 확인하며,
이미 올라간 원본의 누락된 구분은 같은 원본 지문을 대조한 제한된 보완 경로로 채운다.
새 핀 요청에 확인한 구분 지문을 동결하되, 기존 outbox 본문은 변경하지 않는다.
최초 unlocated 생성에서 멈춘 옛 근거 오류는 캐시 제외 근거를 검증한 뒤 한 번 재시도한다.
이미 수용된 v2 행동이 있는 산책의 새로운 보완은 서버가 보류한다. 원본을 자동 삭제·재생성하지 않는다.
서버 지원 배포가 앱 전환보다 먼저여야 한다. 이 변경에 SQL/Room schema 추가는 없다.

## 기록과 위치

진행 중인 산책에서 킁킁·배설·짖기를 누르면 GPS 품질과 관계없이 각각의 행동을 저장한다.
원본 fix와 같은 writer 큐에서 행동 content와 초기 pin을 한 Room 트랜잭션으로 쓴다.
성공 안내는 저장 후 표시한다. 반복 탭은 별개의 ID이고 핀 확정은 행동 수를 늘리지 않는다.

기존 정상 GPS 선택에서 받아들인 원본 client_seq/chain/시각이 일치할 때만 observed다.
이 경우 content.location과 pin은 원본을 유지한다. fallback에서는 content.location=null이고
표시 좌표만 pin에 저장한다. 지도·지난 산책·일기는 같은 표시 투영을 사용하며 원본 GPS,
latestMomentFix, 점령·사진 인증, 경로 좌표에는 추정 결과를 넣지 않는다.

위치 근거가 없으면 목록에 행동을 남기고 가짜 지도 좌표를 만들지 않는다. 위치 상태는
‘위치 추정 중’, ‘추정 위치’, ‘마지막 확인 위치’, ‘위치 없이 남긴 행동’으로 표시한다.
위치가 없어도 편집·삭제·동기화 대기 안내와 짧은 산책 보관 판단에 포함한다.

## 기한과 복구

초기 pin의 8초 기한은 연장하지 않는다. 앱 scope 타이머와 네트워크 조건 없는 WorkManager가
동일한 Room 비교 후 갱신을 사용한다. OS가 작업을 늦게 실행해도 원래 관측 기한까지만 계산한다.
일시정지·종료·위치 수집 중단은 실제 중단 시각까지의 관측으로 종료한다.
앱 시작과 로그인 복구에서는 현재 계정의 미확정 로컬 핀을 원본 로그로 한 번 종료한다.
단, 서비스가 소유한 활성 산책은 화면 재생성·인증 갱신으로 복구가 호출되어도 원래 8초
관측 기한을 유지한다. 기한이 지났으면 deadline으로 종료하고, 서비스가 소유하지 않는
이전 산책만 recovered로 조기 종료한다.
다른 기기에서 받은 provisional은 로컬 생성 chain 정보가 없으므로 임의 재계산하지 않는다.

삭제는 content·pin·동결 요청 본문을 함께 지운다. 삭제 뒤 계산/ACK/GET이 도착해도 복원하지 않는다.
계정·세션·이전 pin을 비교하며, 새 계정으로 이전 작업을 옮기지 않는다.

## Room 11→12

walk_entry에 pinPayload, pinRevision, isV2, pinDirty, pinChainIndex, pendingRequest를 추가한다.
기존 content/revision/mutation/dirty/error와 경로·동행견·사진·검토본·분석은 보존한다.
기존 기록은 v1로 남기고 과거 raw 참조를 만들지 않는다. v1 분석 stamp 계산도 그대로 유지한다.
새 스키마는 app/schemas 아래의 12.json에 포함한다. 파괴적 마이그레이션은 사용하지 않는다.

## 동기화

기존 산책 종료 후 raw 업로드/finalize 순서를 유지하고 그 뒤 기록을 보낸다. GPS 0개도 허용한다.
진행 중에는 Room 저장과 로컬 핀 확정을 수행하며 실시간 서버 업로드를 새로 추가하지 않는다.

capabilities에서 v2 읽기와 신규 쓰기/정책 지원을 확인한다. 옛 서버나 신규 쓰기 중단 상태에서는
기기 원본을 보관하고 전달을 재시도한다. nullable 행동을 v1으로 바꾸지 않는다. 기존 v1 기록은
옛 경로를 유지하며 v2 목록/프로필의 legacy 표현도 읽는다.

전송 직전에 mutation ID와 전체 요청을 Room에 동결한다. ACK 유실 시 그대로 재전송한다.
그사이 내용 정정이나 위치 확정이 발생하면 ACK는 서버 revision만 전진시키며 후속 요청은
새 mutation ID로 전송한다. 409는 서버 최신 상태와 대조하고 content 충돌은 사용자 확인을 받는다.
핀 확정 요청 중 발생한 로컬 내용 편집도 동결 당시 내용과 원격 내용을 비교한다. 원격 내용이
달라졌으면 정정을 보류하고, JSON 순서나 같은 시각의 표기만 달라졌으면 충돌로 취급하지 않는다.
원격 terminal 핀과 삭제 표식은 최종 상태로 수용한다. 422/426은 원본을 유지하며 기록에 오류를 표시한다.

pin=null인 메모는 그것만으로 v2로 분류하지 않는다. 기존 메모의 분석 stamp와 v1 전송을 유지하고,
이미 확인된 v2 기록 또는 v1 요청에 대한 서버의 명시적 426 응답을 통해서만 전송 버전을 바꾼다.

일시정지 후 재개 좌표가 이전 행동의 ‘위치 없음’을 거부하지 않도록 terminal pin에 선택 필드
observation_cutoff_at을 포함한다. DEV #357의 pin_observation_cutoff_supported capability가
이를 알린다. 이전 서버에서도 동일한 관측 창이면 필드를 생략할 수 있지만, 중간에 관측을
종료한 기록은 지원 서버를 기다린다. 이미 동결된 요청의 본문은 협상 과정에서도 바꾸지 않는다.
서버는 cutoff 이전 원본·참조와 시간 범위를 검증한다. 추가 SQL 변경은 없다.

## 검증과 활성화

관련 Room·추정기·동기화·writer·화면·사진·일기·프로필·스토리보드 테스트 196개를 통과했다.
관측 cutoff 보완 후 핀 저장·동기화 19개와 debug 빌드를 다시 검증했다. 마지막 표시/원본 분리에서는
관련 65개와 debug 빌드를 통과했다. 기존 v1 지난 산책의 장소 묶음도 보존한다. 숫자는 중복 실행을 포함한다.
서버 cutoff/요청 해시 호환성을 포함한 API 테스트 31개와 변경 Python ruff 검사도 통과했다.

리뷰 보완에서 회귀 사례 8개를 추가했다. ActionPinReviewTest·ActionPinStoreTest·WalkEntryV2SyncTest·
WalkDaoTest·WalkEntryStoreTest·WalkStoryboardSyncTest를 명시적으로 선택해 총 68개 통과(실패/skip 0),
debug 빌드 통과를 확인했다. 활성 산책 복구와 기한 만료, 이전 산책 복구, 기존 메모 stamp,
426 이후 메모 정정, 핀 전송 중 내용 충돌 및 동일 시각 표기의 호환성을 검증한다.

실기기의 FGS/절전·강제 종료·지도 표시와 실제 인증 서버 연동은 아직 검증하지 않았다.
설치·서버 배포·플래그 활성화는 이번 작업에서 실행하지 않았다. DEV SQL은 사용자 지정 DB에
이미 적용됐지만 서버 코드는 별도 배포가 필요하다. Room 이관은 이 버전의 앱을 실행할 때 수행된다.

APP #233 / DEV #371에서 장면 소비자를 연결했다. 활성 v2 기록은 capability의
`storyboard_formats`에 v5가 있을 때 `walk-storyboard-candidates-v5`만 요청한다.
미확정/미동기화 핀은 분석을 기다리며, 지원 없는 서버에서는 로컬 행동을 유지한다.
기존 기록은 v4/v3/v2 협상을 유지한다. 토큰은 JWE를 포함한 불투명 값으로 취급하고,
인증 세션의 소유자와 Room 소유권을 확인하며 서버 응답을 반영하기 전에 다시 비교한다.

v5 장면의 핀은 원본 observation과 분리하고, 근거 화면에 GPS/추정/마지막 확인/위치 없음과
핀 버전을 표시한다. 지도는 연결된 행동의 핀을 사용하고, 전체 pin JSON은 원본 장면 payload에
보존한다. 위치 없는 행동도 분석·확인 대상에 남는다. 서버 주변 정보는 확정 좌표로 조회하며
추정 위치 한정 문구를 유지한다. 이 단위는 별도 주변 정보 카드 UI를 추가하지 않는다.

관련 동기화·장면 파서·일기 테스트 8개 클래스의 41개와 debug 빌드를 통과했다.
좌표 형식 검증 보완 후 신규 StoryboardPinSyncTest 6개 및 debug 빌드를 다시 통과했다.
추가 Room/SQL 마이그레이션은 없다. 서버·worker 배포, 실제 인증 서버/실기기 통합 검증,
최소 지원 앱 버전 정책 확인 이후 신규 v2 쓰기를 활성화해야 한다.
