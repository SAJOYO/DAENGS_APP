# 점령지 접근 상태와 행동 자격 — APP #223

조회 전용에서도 장소별 실제 거리와 GPS 기반 접근 상태를 계산한다. 서버 세션 연결이
완료되지 않았다는 이유로 GPS를 불확실하다고 판단하지 않는다. 화면의 버튼·링·자동 안내를
바꾸기 전에, 이들이 사용할 위치 판정과 행동 자격을 분리한 단계다.

## 상태 계약

`TerritoryGameSite.proximity`는 현재 위치와 장소 좌표로 계산한다. 장소 조회 응답의
`TerritorySite.distanceMeters`는 조회 중심 기준일 수 있으므로 사용하지 않는다.

| 상태 | 조건 |
| --- | --- |
| `UNAVAILABLE` | 신뢰할 수 없는 위치, 누락·음수·비유한 거리/오차, 또는 반경 안이지만 오차를 더하면 경계를 넘음 |
| `APPROACHING` | 신뢰할 수 있는 위치이고 측정 거리가 반경 밖임. 실제 이동 방향을 뜻하지 않음 |
| `IN_RANGE` | 거리 + GPS 오차가 반경 이하임 |

측정할 수 있는 거리·오차는 `UNAVAILABLE`에서도 남을 수 있다. 유효 숫자가 있다는 것만으로
버튼을 열면 안 된다. 접근 반경은 로컬 정책과 온라인 영역표시의 기존 20m를 유지한다.
온라인 사진 촬영은 같은 판정 함수에 기존 10m를 전달한다.

`evaluateTerritoryProximity`에는 계정·세션·참여견·점유 입력이 없다.
`SiteInteraction`은 이 판정에 산책 기록 상태를 더하며, 최종 행동은 반드시 공급자의
`canMark` / `canPhotograph`를 사용한다. 서버 세션 확인, 참여견, 점유 조회, 기존 시도와
사진 상태의 제한은 그대로다. 조회 전용은 `IN_RANGE`여도 두 행동을 허용하지 않는다.

## 위치 입력과 갱신

- `WalkViewModel`이 화면 위치를 공급자 snapshot에 전달한다. 산책 전·일시정지에는 화면
  위치를 우선하고 없으면 최신 산책 위치를 사용한다. 별도 위치 구독을 만들지는 않는다.
- 기록 중에는 반드시 `latestMomentFix`를 사용한다. 화면 위치가 양호해도 누락·mock·속도/
  정확도 거부·오류가 있는 기록 위치를 대신하여 행동을 열지 않는다.
- 정밀 위치 권한, 유효 좌표, mock 여부, 단조 시계 기준 최신성을 검사한다. 온라인은 기존
  10초를 포함하는 경계를 사용하고, 로컬은 `TerritoryGamePolicy.maxFixAgeNanos`를 따른다.
- 접근 상태는 **snapshot 호출 시점**의 값이다. 독립 만료 타이머는 추가하지 않았다.
  후속 주변 안내에서 값을 오래 유지한다면 갱신·만료 처리를 연결해야 한다.
  영역표시 요청과 촬영 시작은 기존처럼 당시 상태로 다시 판정한다.

## 검증

2026-09-09, dev `a1861b3` 기준. Windows / JBR 25.0.2 / Android SDK 37.0 / Gradle 9.5.0.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.territory.TerritoryProximityTest' --tests 'com.daengs.app.territory.TerritoryClaimTest' --tests 'com.daengs.app.map.features.territory.TerritoryLocationEvidenceTest' --tests 'com.daengs.app.map.features.territory.TerritoryGameControllerTest' --tests 'com.daengs.app.map.features.territory.ServerTerritoryGameProviderTest' --tests 'com.daengs.app.territory.TerritoryActionSyncTest' --tests 'com.daengs.app.territory.ServerTerritoryPhotosTest' --tests 'com.daengs.app.ui.walk.WalkViewModelTest' :app:assembleDebug --console=plain
```

대상 70개 중 69개가 통과했다. 남은 하나는 #221 이전의 release 전체 비활성 기대값이었다.
현재 계약인 조회 전용으로 수정한 뒤 아래 클래스를 재실행해 12개 모두 통과했다.
각 클래스의 최종 결과는 총 **70개 통과, 실패·오류·skip 0개**다. debug 빌드도 성공했다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.territory.TerritoryActionSyncTest' --console=plain
```

거리·오차 경계, 오래된 위치·미래 시각·mock·누락 값, 산책 전 화면 위치, 참여견 없음,
일시정지, 점유 미확인, 서버 세션 연결 대기, 20m 영역표시/10m 사진을 확인한다.
저장소 전체 테스트와 release 재빌드는 실행하지 않았다. 실제 API·GPS·카메라의 기기 검증은
남아 있으며, API 주소·지도 키를 넣지 않은 검증 빌드다.

## 다음 단위

주변 후보 공급과 수동 선택/근접 대상 분리 → 접근 안내 UI → 지도 표현 순서다.
#205의 인증 영역 보호·재도전 정책은 별도 작업이다. 이번 PR은 지도에 불러온 장소만 계산하며,
지도 밖의 주변 후보 조회나 접근 알림을 제공하지 않는다.
