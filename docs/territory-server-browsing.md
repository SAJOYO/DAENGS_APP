# 서버 점유 조회 — APP 연결 1단계

[APP #161](https://github.com/SAJOYO/DAENGS_APP/pull/161)은 기존 산책 지도에 서버 점유 조회를
연결한다. 서버 계약은 [Dev #260](https://github.com/SAJOYO/DAENGS_dev/pull/260)의
`docs/territory-ownership-api.md`다. 서버 DB 마이그레이션/배포는 별도이며 이 작업에서 실행하지 않는다.

## 켜지는 범위

| 빌드 | 공급자 | 동작 |
| --- | --- | --- |
| 일반 debug | 기존 메모리 점유·페이크 사진 | 지금의 로컬 플레이 유지 |
| debug + `-PterritoryServerRead=true` | 서버 점유 조회 | 산책 전/중 점유 읽기만 제공 |
| release | 게임 비활성화 | 이번 조회 실험을 출시 빌드에 노출하지 않음 |

기존 `API_BASE_URL`과 `SessionProvider.freshSession()`을 사용한다. 로그인해야 공유 점유를
읽을 수 있으며 비로그인 상태에서는 HTTP를 보내지 않고 로그인 안내를 표시한다. 일반 산책·일기
촬영은 기존 정책을 따른다. 서버 모드에서 점유 조회 실패가 로컬 점령 성공으로 바뀌는 fallback은 없다.

```powershell
# 기본 로컬 플레이
./gradlew.bat :app:assembleDebug
# DB 적용/배포 후 실제 조회를 켜서 검증할 빌드
./gradlew.bat :app:assembleDebug -PterritoryServerRead=true
```

플래그는 커맨드에만 주고 `local.properties`나 운영 환경을 변경하지 않는다. 테스트에서도
서버 주소·토큰은 가짜 HTTP 서버/함수로 주입하며 실제 회원 API에 접속하지 않는다.

## 구조와 화면

- `WalkViewModel`은 `TerritoryGameProvider`를 받는다. 기존 `TerritoryGameController`가 로컬
  액션을 제공하고 `ServerTerritoryGameProvider`가 비동기 조회 결과를 같은 지도 상태로 변환한다.
- 장소 위치/게임판은 기존 `TerritorySiteRepository`가 소유한다. `GET /app/territory/occupancies`
  응답을 `site_id`로 합치며 100개씩 나눠 조회한다. 응답 누락·중복·다른 장소·깨진 상태는 실패다.
- 지도에 전봇대가 있고 점령 레이어가 보일 때 조회한다. 같은 화면은 15초 간격으로 갱신하며
  지도 이동으로 장소 목록이 바뀌면 다시 요청한다. 선택 변경만으로는 요청하지 않는다.
- 레이어 숨기기·화면 이탈·Activity 백그라운드에서 조회를 중단하고 돌아오면 새로 조회한다.
  서버 조회 상태만 멈추며 Foreground Service의 산책 기록은 계속된다.
- 로딩·실패는 `점유 확인 전`, 서버가 명시적으로 null을 반환한 경우만 `미점유`다. 갱신 중에는
  같은 계정/요청 장소의 직전 결과를 유지하고, 조회가 실패하면 오래된 소유권을 지운다.
- 서버 모드는 산책 중에도 대표견 선택·점령·인증 촬영·접근 링을 열지 않는다. 일반 일기 카메라는
  계속 사용할 수 있다. 장소 카드에 서버가 준 강아지 이름과 인증 상태가 표시된다.
- 공유 응답에는 타인의 원본 세션/시도 ID가 없다. `TerritoryOccupancy`의 해당 필드를 nullable로
  만들어 없는 값을 그대로 표현한다. 임의 ID를 만들어 채우거나 남의 점유 갱신을 내 성공 효과로
  재생하지 않는다. 로컬 판정 구현은 계속 실제 ID를 사용한다.
- 요청 세대와 로그인 계정을 확인해 늦은 응답을 버린다. 응답 원문/토큰을 오류 문구에 넣지 않으며
  인증 헤더를 가진 요청은 HTTP redirect를 자동으로 따라가지 않는다.

현재 조회 캐시는 ViewModel 생애의 메모리다. 점유 요청 영속 저장이나 Room 마이그레이션은 없다.
착수 시 Room v10이었으며 최신 dev의 스토리보드 복구 변경을 병합해 v11을 유지한다.
이 PR 자체는 DB 스키마를 변경하지 않는다.

## 검증

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.features.territory.*' --tests 'com.daengs.app.territory.*' --tests '*WalkViewModelTest' --tests '*WalkTerritoryUiTest' --tests '*TerritoryFeedbackUiTest' :app:assembleDebug
```

새 테스트는 실제 localhost HTTP의 인증 헤더·GET·쿼리·JSON 계약, 로그인 없음, 조회 실패/복구,
지연 응답·계정 변경·장소 요청 교체, 100개 묶음, 모드 기본값, 화면 이탈/백그라운드의 조회 중단을
확인한다. 기존 로컬 판정·촬영·지도/성공 효과 회귀도 함께 검사한다.

Robolectric Compose에서 실제 `WalkScreen`에 서버 공급자 결과를 넣어 점유/실패 카드와 액션 분리를
검증한다. `app/build/reports/walk-territory/server-browsing.png`, `server-error.png`가 화면 결과다.
이 캡처는 `showMap=false`여서 Naver SDK 타일/실기기 GPS·카메라를 검증한 결과가 아니다.

## 다음 PR

2단계 구현과 추가 플래그는 [서버 세션·영역표시 #164](territory-server-actions.md)에 정리했다.
아래는 #161 당시 나눈 작업 범위다.

2단계는 게임 세션 등록/phase·영역표시·최초 요청 영속 저장 및 서버 성공 결과의 효과 연결이다.
3단계는 촬영 증거·티켓·시도 연결·업로드·판정 조회/복구다. 이 단계에서 서버의 영역표시 20m와
인증 10m, 최종 FAILED 이후 재촬영 계약도 UI와 맞춘다. 서버 쓰기는 이번 PR에 포함하지 않는다.
