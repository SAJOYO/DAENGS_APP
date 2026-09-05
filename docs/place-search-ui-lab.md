# 시설 검색 Kotlin 검토 화면

Geo 웹 검토판에서 합의한 UI를 Kotlin으로 옮기는 1단계 개발 화면이다.
일반 운영 진입점은 유지하고 debug 소스셋의 별도 Activity에서 저장 응답으로 검토한다.

## 실행

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.daengs.app/.ui.places.lab.PlaceSearchLabActivity
```

Activity와 fixture는 debug 전용이며 release manifest에는 진입점이 없다.
지도 켜기를 누르면 기존 MapHost/NaverMapSurface를 사용한다. 지도 키·네트워크가 필요하다.
기본은 네트워크 없는 번호 선택으로 카드 선택을 검토한다. GPS 권한은 요청하지 않는다.

## 구현 범위

- 입력과 적용 조건, 선택 장소와 펼친 장소를 분리한 ViewModel.
- 검색 제출 이전에는 결과 불변. 이전 요청 취소 및 요청 버전으로 늦은 응답 무시.
- 일반 검색/AI 모드, 2행 카테고리, 전체보기와 기타 구분, 프로필 이름 복수 선택 자리.
- 반려견·거리·주차·설정을 같은 줄에 표시. 결과 개수·정렬과 서랍식 카드.
- 등록 상태와 크기 평가를 구분하고 제한 원문·출처 날짜를 펼친 카드에 표시.
- 기존 PlaceModels에서 facts/evaluations의 restrictions JSON을 손실 없이 보존.
- 강남/성수 지역 전환, 실패 재시도. 로딩·빈 결과·권한·미수집은 상태 모델로 분리.
- 지도/번호 선택은 펼침을 바꾸지 않으며 한 카드만 펼침. Preview 제공.

검색은 선택 지역·업종의 저장된 장소명·주소 부분 검색이다. 반경은 3km,
평가 조건은 대형견 30kg·3세로 고정한다. 보리·초코는 가상 이름이며 다견 평가와 연결되지 않는다.
AI 제출은 미연결 안내만 표시하고 기존 조건을 유지한다. 전화·길찾기는 안내만 표시한다.
서버 일반 검색·실제 프로필·다견 평가·AI 연결은 후속 단계다.

## 자료 출처

`app/src/debug/assets/place_search_lab.json`은
rkbuhtig/DAENGS_geo@242ac73의 app/static/place_ui_lab/fixtures.json을 복사했다.
2026-09-05 공개 출시 API에서 기록한 4개 지역 × 6개 조건 응답이다.
카페·음식점 표본만 있고 현재 정책을 보증하지 않는다. 계정/토큰은 포함하지 않는다.
단위 테스트에는 성수 대형견 응답 한 개를 추출해 사용한다.

구현 계획: https://github.com/rkbuhtig/DAENGS_geo/blob/experiment/place-ui-web/docs/explorations/facility/place-search-implementation-plan.md

## 검증 한계

실기기·에뮬레이터는 현재 PC 환경에서 사용할 수 없다. APK 빌드와 단위 테스트 결과는
PR에 기록하며 실제 지도 렌더링, 작은 화면·큰 글자, 키보드, 회전, 접근성은 Android 실행
환경에서 별도로 확인해야 한다. 이 검토 화면을 운영 화면에 연결하기 전에 필요한 검증이다.
## 로컬 검증 기록 (2026-09-05)

Windows에서 저장소 외부 `.tooling` 폴더의 JDK 25와 Android SDK를 사용했다.
재부팅·Docker·Android Studio 설치 없이 debug APK 빌드와 전체 단위 테스트를 실행했다.
최종 결과와 커밋은 PR #143에 기록한다. 앱 표시 검증을 완료했다는 뜻은 아니다.
## 실제 API 연결 검토 (2단계 첫 연결)

```powershell
adb shell am start -n com.daengs.app/.ui.places.lab.PlaceSearchLiveActivity
```

`PlaceSearchLiveActivity`는 debug 전용이며 **실제 개발 서버**
`http://daengback.weareithero.cloud`를 사용한다. 자동 최초 검색이 발생한다.
중심은 성수 고정 좌표이고 프로필 조건은 없다. 고정 좌표를 기기 GPS 측정값으로 표시하지 않는다.
지도 버튼으로 Naver 지도를 켜면 이동한 중심의 '이 지역 검색'을 사용할 수 있다.

새 `ConnectedPlaceSearchScreen`은 기존 PlacesViewModel → PlaceSessionCoordinator →
PlaceDiscoveryController → PlaceRepository/PlaceApi의 상태·액션을 그대로 사용한다.
별도의 LabViewModel로 실제 검색하지 않는다. 기존 PlacesRoute도
`useConnectedSearch=true`로 같은 화면을 쓸 수 있으며 기본 운영 화면 선택은 유지한다.
이름 입력·AI 전환은 요청하지 않고, 제출 시 실제 이름 검색을 실행한다.
주차 변경·지도 재검색은 기존 적용 이름을 유지하며 결과의 truncated는 개수의 +로 표시한다.
전화는 ACTION_DIAL, 길찾기는 기존 Journey·handoff 흐름에 연결된다.

연결 화면은 단일 업종과 전체보기, 1·3·5·10·20km 반경 선택을 지원한다.
AI와 다견 선택은 아직 실제 요청에 연결하지 않는다. 주소/지역명 검색을 지원한다고 표시하지 않는다.
출시 서버의 이름 echo 검증을 제거하지 않았으며 출시 배포·DB 변경은 하지 않았다.

검증은 새 Compose 화면 이벤트, 운영 ViewModel/Coordinator, HTTP echo 계약 테스트를 포함한다.
실기기 GPS·지도·전화·외부 앱 handoff는 별도 검증 대상이다.


## 반경·전체보기 확장

서버 요청은 최대 6종류 제한을 유지한다. 전체보기는 18종류를 6개씩 최대 3개 요청으로
동시에 조회하며, 하나라도 실패하면 형제 요청을 취소하고 전체 오류로 처리한다.
이전 검색의 늦은 결과는 기존 requestGeneration에서 무시한다. 재시도는 같은 반경·이름·
종류 묶음을 사용한다. 다른 업종이나 지도 중심으로 이동해도 반경을 유지한다.

종류별 상한은 기존 50개, 전체보기는 최대 900개 원본 항목이다. 그룹·분류·원천은
응답에 보존하고 화면에서 canonical key 중복을 접어 거리순으로 표시한다. 주차 선호 시
500m 구간 내 주차 가능 우선이며 false/unknown은 같은 순위다. 동률은 원천·ID로 고정한다.
어떤 그룹이라도 truncated이면 개수에 +와 반경 축소 안내를 표시한다. 페이지/전체 total은 없다.

2026-09-05 개발 서버 성수 5km·주차 우선·limit 50으로 3묶음을 조회했다.
18종류 그룹 모두 반환됐고 원본 합계 145개, 펫샵·미용 두 그룹은 truncated였다.
이 수치는 중복 제거 전이며 정확한 전체 시설 수나 화면 최종 개수로 사용하지 않는다.
운영 배포·DB 변경은 하지 않았다.
