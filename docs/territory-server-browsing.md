# 서버 점유 조회 — APP 연결 1단계

[APP #161](https://github.com/SAJOYO/DAENGS_APP/pull/161)은 기존 산책 지도에 서버 점유 조회를
연결한다. 서버 계약은 [Dev #260](https://github.com/SAJOYO/DAENGS_dev/pull/260)의
`docs/territory-ownership-api.md`다. 서버 DB 마이그레이션/배포는 별도이며 이 작업에서 실행하지 않는다.

## 켜지는 범위

현재 기본값은 [APP #221](https://github.com/SAJOYO/DAENGS_APP/pull/221)에서 바뀌었다.
공유 점유 조회는 산책 액션과 독립적으로 일반 앱에서도 제공한다.

| 빌드 | 공급자 | 동작 |
| --- | --- | --- |
| 일반 debug | 서버 점유 조회 | 산책 전/중 점유 읽기만 제공 |
| debug + `-PterritoryServerRead=false` | 메모리 점유·페이크 사진 | 명시적으로 선택하는 로컬 연습 |
| debug + `-PterritoryServerActions=true` | 서버 점유 조회·액션 | 기존 온라인 영역표시·촬영 테스트 |
| release | 서버 점유 조회 | 둘러보기 제공, 점령·촬영 액션 비활성 |

기존 `API_BASE_URL`과 `SessionProvider.freshSession()`을 사용한다. 로그인해야 공유 점유를
읽을 수 있으며 비로그인 상태에서는 HTTP를 보내지 않고 로그인 안내를 표시한다. 일반 산책·일기
촬영은 기존 정책을 따른다. 서버 모드에서 점유 조회 실패가 로컬 점령 성공으로 바뀌는 fallback은 없다.

```powershell
# 기본 서버 둘러보기 (명시적인 territoryServerRead=true도 동일)
./gradlew.bat :app:assembleDebug
# 서버 없는 로컬 연습
./gradlew.bat :app:assembleDebug -PterritoryServerRead=false
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
- 초기 로딩은 `점유 확인 전`, 통신 실패는 `점유 조회 실패`, 인증이 없으면 `로그인 필요`다.
  서버가 명시적으로 null을 반환한 경우만 `미점유`다. 갱신 중에는
  같은 계정/요청 장소의 직전 결과를 유지하고, 조회가 실패하면 오래된 소유권을 지운다.
- 조회되지 않은 지도 마커는 55% 불투명도와 `확인 전` 문구로 구분한다. 선택하면 구체적인
  조회 상태가 보인다. 공급자가 없는 지도도 미점유로 바꾸지 않는다.
- 서버 조회 전용 모드는 산책 중에도 대표견 선택·점령·인증 촬영·접근 링을 열지 않는다.
  일반 일기 카메라는 계속 사용할 수 있다. 장소 카드에 강아지 이름·인증 상태·점령 시각과
  내 점령 여부를 표시한다. 점령 시각은 서버의 occupied_at을 기기 시간대로 표현한다.
- 먼 지역도 지도에 불러온 장소를 선택하면 같은 정보를 읽는다. 점유 조회는 현재 GPS의
  거리·신뢰도나 산책 세션을 요구하지 않는다. 장소 목록의 지도 이동·권한 정책은 유지한다.
  조회 전용 모드에서 선택할 때 내 위치까지 포함해 지도를 축소하지 않고 보고 있던 지역을 유지한다.
- 공유 응답에는 타인의 원본 세션/시도 ID가 없다. `TerritoryOccupancy`의 해당 필드를 nullable로
  만들어 없는 값을 그대로 표현한다. 임의 ID를 만들어 채우거나 남의 점유 갱신을 내 성공 효과로
  재생하지 않는다. 로컬 판정 구현은 계속 실제 ID를 사용한다.
- 요청 세대와 로그인 계정을 확인해 늦은 응답을 버린다. 응답 원문/토큰을 오류 문구에 넣지 않으며
  인증 헤더를 가진 요청은 HTTP redirect를 자동으로 따라가지 않는다.

현재 조회 캐시는 ViewModel 생애의 메모리다. 점유 요청 영속 저장이나 Room 마이그레이션은 없다.
착수 시 Room v10이었으며 최신 dev의 스토리보드 복구 변경을 병합해 v11을 유지한다.
이 PR 자체는 DB 스키마를 변경하지 않는다.

## 검증

아래는 SDK가 있는 환경에서 실행할 검증 명령이다. #221 작업 환경에는 Android SDK와
Gradle 캐시가 없어 새 회귀 테스트·Compose 렌더·APK 빌드를 실행하지 못했다.
기존 #161 검증 설명을 #221의 통과 결과로 읽지 않는다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.features.territory.*' --tests 'com.daengs.app.territory.*' --tests '*TerritoryBoardPresentationTest' --tests '*WalkViewModelTest' --tests '*WalkTerritoryUiTest' --tests '*TerritoryFeedbackUiTest' :app:assembleDebug :app:assembleRelease
```

출시 전 실제 API와 기기에서 다음을 확인한다.

1. debug와 release가 각각 의도한 API 주소를 사용하고 인증된 `GET /app/territory/occupancies`가 정상 응답하는지 확인한다.
2. 계정 A의 실제 점유가 계정 B의 먼 지역 선택 카드에 강아지·인증·점령 시각으로 보이는지 확인한다.
3. 통신 실패·로그아웃 때 기존 점령자와 점령 시각이 사라지고 미점유로 바뀌지 않는지 확인한다.
4. 산책 시작 전과 먼 지역에서도 조회되며 조회 전용 빌드에 점령/촬영 버튼이 열리지 않는지 확인한다.

이 PR은 운영 DB·서버 배포·회원 API에 접근하지 않았다. 기존 점유 API와 데이터가 있는
서버로 새 APK를 빌드·설치해야 실제 점령자가 보인다. 주변 자동 접근 안내는 다음 작업이다.

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
