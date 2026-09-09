# 주변 후보와 수동 선택 분리 — APP #225

지도에서 먼 곳을 둘러보더라도 내 주변 장소를 별도 공급한다. [#223의 접근 판정](territory-proximity.md)을
사용하며, 근접 대상이 바뀌어도 수동 선택 카드·카메라·점령 및 촬영 대상은 바꾸지 않는다.

## 데이터 경계

| 상태 | 역할 |
| --- | --- |
| `WalkUiState.territory` | 기존 지도 조회 목록과 수동 `selectedSiteId`. 카메라 조회 정책 유지 |
| `WalkUiState.nearbyTerritory` | 기기 위치 기준 후보, 로딩·실패·조회 중심·잘림 여부 |
| `territoryGame.targetId` / `target` | 기존 수동 선택 카드와 행동 자격의 대상 |
| `territoryGame.nearbyTargetId` / `nearbyTarget` | 주변 후보 중 신뢰할 수 있는 근접 대상. 자동 선택이나 행동 허가가 아님 |

지도와 주변 목록을 site ID로 합쳐 공급자 snapshot과 점유 조회에 전달한다. 같은 장소는
한 번만 포함하고 지도 목록의 좌표를 우선한다. 점유 조회는 기존 15초 갱신 루프 하나가 담당한다.
지도 마커는 기존 지도 목록만 사용하므로 주변 데이터 도착이 화면 영역이나 선택을 바꾸지 않는다.

근접 대상은 `IN_RANGE` 우선, 실제 기기 거리, ID 순으로 결정한다. 신뢰할 수 없는 위치와
현재 위치에서 300m를 넘는 후보는 제외한다. 범위 안 후보가 없으면 `APPROACHING`인 가장
가까운 후보가 될 수 있다. 서버 응답의 `distance_m`를 실제 기기 거리로 사용하지 않는다.
점유를 아직 조회하지 못했거나 로그인이 없어도 위치상 후보는 존재할 수 있다.

## 조회와 생명주기

- 점령 지도 레이어가 활성화된 화면의 foreground에서만 실행한다. 기존 위치 구독을 사용하며
  산책 중에는 Foreground Service의 기록 위치를 따른다. 새 GPS 구독은 만들지 않는다.
- 정밀 위치 권한, 좌표, mock 여부, 기록 위치 품질, 유효 GPS 오차와 기존 10초 최신성을 검사한다.
  카메라 이동이나 `followDevice=false`는 이 피드를 멈추거나 조회 중심을 바꾸지 않는다.
- 기존 장소 API를 기기 중심 반경 300m, 최대 500개로 요청한다. 마지막 요청 중심에서 100m
  이동하거나 마지막 요청 후 60초가 지나면 갱신한다. 같은 위치의 실패는 요청 시각 기준 30초 뒤 재시도한다.
- 갱신 중·실패 시 후보를 비운다. 잘린 응답은 `truncated`를 보존한다. 최대 개수 제한이 있는
  조회이므로 모든 주변 장소가 포함됐다고 보장하지 않는다.
- 1초 간격으로 위치 최신성을 다시 평가한다. GPS가 끊겨도 후보가 계속 활성 상태로 남지 않는다.
  만료 후 제거까지 최대 약 1초가 걸릴 수 있다. 행동은 기존 요청·셔터 시점에 다시 검사한다.
- 권한 상실, 레이어 숨김, 화면 이탈, 백그라운드 진입 시 후보를 비우고 요청을 취소한다.
  요청 세대로 늦은 응답도 무시한다. 다시 활성화되면 신뢰할 수 있는 위치에서 새로 조회한다.

## 검증 범위

2026-09-09, dev `2a45a0e` 기준으로 대상 7개 클래스의 **61개 테스트가 모두 통과**했고
debug 빌드도 성공했다. Windows / JBR 25.0.2 / Android SDK 37.0 / Gradle 9.5.0 환경이다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.features.territory.TerritoryNearbyControllerTest' --tests 'com.daengs.app.map.features.territory.TerritoryBoardControllerTest' --tests 'com.daengs.app.map.features.territory.TerritoryLocationEvidenceTest' --tests 'com.daengs.app.map.features.territory.ServerTerritoryGameProviderTest' --tests 'com.daengs.app.map.features.territory.TerritoryFeedbackTrackerTest' --tests 'com.daengs.app.ui.walk.TerritoryBoardPresentationTest' --tests 'com.daengs.app.ui.walk.WalkViewModelTest' :app:assembleDebug --console=plain
```

이후 #224가 병합된 dev `58ae6b5`를 반영한 `68cf061`에서 변경 경계의 ViewModel·산책 화면
테스트 30개와 debug 빌드를 다시 통과했다. 두 실행의 중복을 제외한 대상은 **73개**이며,
각 클래스의 최종 결과에 실패·오류·skip은 없다. 전체 테스트와 release 빌드는 실행하지 않았다.
API 주소·지도 키를 넣지 않은 검증용 빌드다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.WalkViewModelTest' --tests 'com.daengs.app.ui.walk.WalkTerritoryUiTest' :app:assembleDebug --console=plain
```

주변 조회의 이동·시간 경계, 실패 재시도, 잘림, 미지원 지역, 취소 후 늦은 응답과 요청 교체를
대상 테스트로 검증한다. ViewModel에서는 먼 지역 선택 유지, 기기 이동에 따른 근접 대상 교체,
점유 조회의 목록 합치기, 위치 구독 1개 유지, 위치 만료·권한·화면 생명주기를 확인한다.
근접 후보에 대한 영역표시/촬영 입력이 기존 수동 선택 검사에서 차단되는지도 확인한다.

실제 서버·Naver 지도·GPS·카메라의 기기 통합 검증은 별도다. 새 알림·접근 카드·지도 효과는
이 PR에 포함되지 않는다. 후속 UI는 근접 대상을 표시할 때 사용자가 선택한 카드와 구별하고,
후보 교체 시 안내 반복 방지와 필요한 명시적 선택 동작을 연결해야 한다.
