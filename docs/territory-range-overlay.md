# 전봇대 원형 범위 — APP #226

사용자가 승인한 방향은 전봇대 중심의 지도 원이다. 별도의 접근 카드나 자동 선택은 추가하지 않는다.

## 표시 계약

- 전봇대를 직접 누르면 해당 장소의 영역표시 반경을 표시한다. 기본 조회 모드·산책 전·일시정지에도
  선택한 원을 볼 수 있다. 선택 해제 시 수동 원은 사라지며 자동 표시 조건을 만족하는 원은 남는다.
- 기기 중심 주변 후보 중 실제 거리가 60m 이하인 장소의 원을 자동 표시한다. 한 번 표시한 원은
  70m를 초과하면 지운다. 조회 중심 기준 `distanceMeters` 대신 현재 위치와 장소 좌표를 사용한다.
- 원 반경은 `TerritoryGameState.radiusMeters`를 그대로 사용한다. 현재 기본 정책은 **20m**다.
  HTML 시안의 30m는 반영하지 않았다. 사진 인증의 10m 판정도 바꾸지 않는다.
- GPS와 오차를 반영한 기존 `proximity`가 `IN_RANGE`면 초록색·‘범위 안’, `APPROACHING`이면
  분홍색·‘영역표시 범위’, `UNAVAILABLE`이면 회갈색·‘위치 확인 중’이다. 원 크기는 변하지 않는다.
- **초록색은 위치 판정이다.** 조회 전용·인증 보호·세션 연결·참여견·기존 시도 등 행동 자격은
  기존 `canMark` / `canPhotograph`를 따른다. 접근 색만으로 버튼을 열지 않는다.
- 신뢰할 수 없는 위치, 누락·비유한·음수 오차, 권한 상실, 일시정지, 레이어 숨김, 화면 이탈,
  백그라운드 전환에는 자동 원과 진입 기억을 지운다. 위치 만료는 #225의 1초 재평가를 사용한다.
  신뢰할 수 있는 GPS라도 오차가 20m 경계를 걸치면 자동 원은 유지하고 회갈색으로 표시한다.

## 지도와 선택

`TerritoryRangeTracker`가 공급자 행동 상태와 별도로 `visibleRangeSiteIds`를 만든다.
지도 목록에 없는 자동 범위 후보만 마커 목록에 보충하며, ID가 같으면 기존 지도 좌표를 우선한다.
자동 원 표시는 카메라·선택 카드·행동 대상을 변경하지 않는다.

보충한 근처 마커를 직접 누르면 그 한 장소를 `TerritoryBoardController`에 넣고 수동 선택한다.
진행 중인 지도 조회 응답이 뒤늦게 도착해도 선택한 장소 하나는 유지한다. 해당 장소가 새 응답에
포함되면 새 데이터를 우선한다. 이를 통해 기존 영역표시·사진 요청의 선택 ID 검사를 그대로 쓴다.

Naver `CircleOverlay`를 전봇대 좌표에 미터 단위로 놓으므로 지도 줌에 따라 화면 크기가 달라진다.
테두리는 2dp, 내부는 약 8% 농도다. 기존 성공 효과는 농도·선 굵기만 잠깐 바꾸며 판정 반경이나
접근 색을 바꾸지 않는다. 범위가 표시된 전봇대에는 접근 문구도 함께 표시한다.

## 검증

2026-09-09, dev `7cec8a6` 기반. Windows / JBR / SDK 37.0 환경에서 대상 테스트를 실행했다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*TerritoryRange*Test' --tests '*TerritoryBoardControllerTest' --tests '*TerritoryBoardPresentationTest' --tests '*WalkViewModelTest' --tests '*MapScenePolicyTest' :app:assembleDebug --console=plain
```

50개 중 49개 통과. 남은 하나는 기존 ‘조회 전용에는 원 없음’ 기대값이었으며 20m 원 표시로 갱신했다.
미리보기 상단 여백을 보완하고 영향받은 두 테스트만 다시 실행해 통과했다. 각 테스트의 마지막
결과를 기준으로 **총 50개 통과, 실패·오류·skip 0개**이며 debug 빌드도 성공했다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*WalkViewModelTest.server occupancy reaches map before walking and refresh stops when hidden' --tests '*TerritoryRangePreviewTest' :app:assembleDebug --console=plain
```

자동 진입/이탈, 경계 오차, 위치 만료·화면 생명주기, 수동/자동 원 공존, 주변 마커의 명시적 선택과
영역표시, 지연된 지도 응답 이후 선택 유지, 조회 전용 행동 차단을 확인했다. 실제 전봇대 리소스와
공통 색을 Compose로 렌더한 검토 이미지는 `app/build/reports/territory-range/preview.png`다.

전체 테스트와 release 빌드는 실행하지 않았다. API 주소와 지도 키 없는 검증 빌드다.
실제 서버·GPS와 Naver SDK의 확대/축소·기울기·터치 통합은 실기기 확인이 남았다.
