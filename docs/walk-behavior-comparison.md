# 행동 기록 돌아보기

산책 목록에서 비교 화면을 열고 반려견과 행동(킁킁·배설·짖기)을 고른다.
최근 30일 또는 90일의 비교 가능한 전체 산책 A와 해당 행동 기록이 있는 산책 B를 같은 지도에서
전환한다. 근거 목록은 기록 시각과 위치 유무를 보여주며, 원본 산책 연결 ID가 있으면
기존 산책 상세 화면으로 연결한다.

## 화면이 말하는 것

두 지도는 같은 격자·계산 방식·색상 척도를 사용한다. 산책마다 공간 시간량을 정규화한
뒤 같은 무게로 평균한 분포이며, 한 산책에 핀이 많아도 그 산책의 무게는 늘지 않는다.
지도의 색은 선택한 산책 전체의 공간 분포다. 행동을 기록한 위치는 개별 핀으로 확인한다.

위치 없는 기록도 행동 기록 수·날짜 수·산책 B에 포함한다. 지도 좌표는 서버 응답의
`pin.point`만 사용하며, 위치가 없으면 원본 `location`으로 되돌려 표시하지 않는다.
현재 핀의 추정·미확정 상태를 실제 관측으로 바꾸어 해석하지 않는다.

빈 A, 빈 B, A=B, 로딩·실패·서버 미지원 상태를 구분한다. 일부 기록을 숨겨 성공한
비교처럼 보여주지 않는다. 공간 자료가 없는 산책은 이 비교에 들어가지 않으므로
전체 행동 기록의 총계와 다를 수 있다.

## 서버 연결

`POST /app/walks/spatial-diary/behavior-comparisons/query`

- `comparison_version`: `walk-behavior-comparison-v1`
- `walk_selector`: 선택한 `pet_id`, 서울 날짜 기준 양끝을 포함하는 `since`·`until`, `context_facets`
- `behavior_code`: `sniffing`, `excretion`, `barking`

서버가 한 snapshot에서 A, B, 행동 근거를 확정한다. 앱이 서로 다른 API 응답을 조합해
산책 집합을 재구성하지 않는다. 현재 로그인 세션으로 조회하고 화면의 조건에 맞는
응답만 표시한다. 원본 산책 상세의 가용성은 이 기기에 저장된 자료에 달려 있다.

서버에는 이 API의 배포와 기존 행동 v2 설정이 필요하다. `404`는 기능 준비 안내로
처리한다. 새로운 DB나 migration, 기기 설치 절차를 추가하지 않는다.

이 화면은 현재 기록을 돌아보는 비교 기능이다. 실제 행동 확률·장소 선호·강아지 성격을
판정하지 않으며, 프로필 영구 저장·LLM 해석·핀 전후 움직임 분석은 포함하지 않는다.

## 검증 경계

서버가 직렬화한 동일한 응답 자료를 앱의 파서와 화면 테스트에서 사용해 계약 연결을
확인한다. 선택 조건, A/B 전환, 기록·위치 없음 표시, 실패 처리를 대상으로 검증한다.
네이티브 지도 타일·폴리곤의 실제 기기 표시와 배포 서버의 데이터 적재 여부는 별도로
확인해야 하며, 로컬 단위 테스트 통과만으로 완료되었다고 간주하지 않는다.

2026-09-09 로컬 검증에서는 새 비교 모델·HTTP·Compose 3개 클래스와 기존 공간 모델·HTTP·격자,
지도 정책·산책 목록 검색 5개 클래스를 선택해 **33개 통과, 실패·skip 0개**를 확인했다.
서버가 직렬화한 자료는 `app/src/test/resources/walk_behavior_comparison_response.json`에 있다.
예제의 격자 좌표는 집계 검증용 합성 값이므로 실제 지도 위치 확인에 사용하지 않는다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.WalkBehaviorComparisonTest' --tests 'com.daengs.app.walk.diary.WalkBehaviorComparisonApiTest' --tests 'com.daengs.app.ui.walk.WalkBehaviorComparisonScreenTest' --tests 'com.daengs.app.walk.diary.SpatialDiaryModelsTest' --tests 'com.daengs.app.walk.diary.SpatialDiaryApiTest' --tests 'com.daengs.app.walk.diary.SpatialDiaryGeometryTest' --tests 'com.daengs.app.map.shell.MapScenePolicyTest' --tests 'com.daengs.app.ui.walk.WalkHistorySearchScreenTest'
.\gradlew.bat :app:assembleDebug
```

Debug APK 빌드도 통과했다. 실기기 설치·서버 배포는 수행하지 않았다.
