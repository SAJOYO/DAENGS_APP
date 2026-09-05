# 지도 검색 UI — PR1

## 합의한 경계

일반 검색이 기본이다. 시설 카테고리는 사실 기반 종류 필터이며 AI 질문/해시태그는 목적을 탐색하는 별도 입구다. 해시태그를 카테고리의 별명이나 로컬 키워드 규칙으로 취급하지 않는다.

- 아이콘 + 이름의 카테고리 메뉴, 검색 입력은 살구색, AI 질문 영역은 보라색.
- 카테고리 바로가기 7개와 **전체 보기**를 제공한다. 전체 보기는 18종 선택 목록을 여는 행동이고, 모든 종류 검색이 아니다.
- 추가 종류를 고르면 마지막 바로가기에 현재 선택이 보인다. 서버 종류 목록은 줄이지 않는다.
- 초안의 공원은 정식 PlaceKind가 아니므로 여행지 전체를 공원으로 바꾸어 표시하지 않는다.
- 선택한 카테고리는 기존 Search 이벤트로 전달한다. 위치/권한/주차 선호/검색 제한/시설 상세/길찾기는 기존 계층이 소유한다.

## PR1에서 실제로 연결되는 것

`PlacesScreen` 상단 카테고리 메뉴 → 기존 `PlacesAction.Search` → 기존 Controller/Repository.
하단 `PlaceDiscoveryPanel`은 결과·주차 선호·상세만 담당한다. 지도 영역은 상단 메뉴 아래에서 측정하고 하단 패널은 남은 높이의 최대 절반을 사용한다. 마커 선택과 카메라 하단 패딩 연결은 유지한다.

`PlaceNameSearchField`와 `PlaceAiQuestionPanel`은 값과 콜백만 받는 독립 UI다. **PR1에서는 실제 PlacesRoute에 넣지 않는다.** 기존 요청에는 장소명 필드가 없고, AI 제안의 적용/복귀 계약도 아직 앱에 연결되지 않았다. 가짜 검색을 실제 검색처럼 노출하지 않는다.

`PlaceAiSuggestion`은 제안 ID와 표시 문구만 가진다. PlaceKind/필터를 참조하지 않는다. 목적 해석은 이후 서버 연결의 책임이다.

## 확인 방법

- Android Studio: `PlaceSearchDesignPreview`에서 전체 초안/320dp 화면, `PlaceSearchInputsLoadingPreview`에서 독립 입력 로딩 상태 확인.
- 실제 화면 Preview: `PlacesScreenPreview`, `PlacesLoadingPreview`, `PlacesErrorPreview`. 지도 SDK는 `showMap=false`로 제외한다.
- Debug APK의 Compose tooling PreviewActivity에서도 전체 초안을 열 수 있다. API/위치 권한 없이 입력과 메뉴를 확인하는 샘플이며 **API 미연결**을 명시한다.

```powershell
adb shell am start -n com.daengs.app/androidx.compose.ui.tooling.PreviewActivity --es composable com.daengs.app.map.features.places.PlaceSearchDesignPreviewKt.PlaceSearchDesignPreview
```

- 타겟 검증: PlaceCategoryMenuTest, PlaceSearchUiTest, 기존 PlaceDiscoveryPanelTest. Compose UI 검증은 기존 Robolectric 설정을 사용한다.
- 앱은 현재 DaengsTheme의 고정 라이트/크기 정책을 따른다. 이번 작업에서 글로벌 테마나 글자 배율 정책을 바꾸지 않는다.

## 다음 연결

1. PR2: 장소명 검색 계약을 서버와 맞추고 일반 검색 입력을 실제 화면에 노출한다. 이름/종류/반경 필터는 서버에서 제한 적용 전에 처리한다.
2. PR3: 별도 AI 질문과 목적 해시태그를 기존 오케스트레이션에 연결한다. 제안 미리보기와 지도 적용을 구분하고 일반 검색 조건/결과로 돌아갈 수 있게 한다. 단순히 종류를 바꾸는 기능으로 대신하지 않는다.
3. PR4: 키보드/하단 패널/지도 이동/요청 취소/화면 복원 등을 통합 검증한다. 실패·빈 결과는 확인 가능한 정보와 다음 행동으로 안내하되 시설 사실을 지어내지 않는다.

새 검색 API나 LLM 레이어를 PR1에서 구현하지 않는다. 채팅 Place 카드 작업(#114)과도 분리한다.
