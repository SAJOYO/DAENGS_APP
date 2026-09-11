# 시설 검색 찜 열람

## 실제 연결 확인 범위 (#304)

2026-09-11: 찜 HTTP·controller·실제 연결 화면과 기존 찜/검색/목록/상세/조건 테스트
9개 클래스, 총 43건을 확인했다. 최초 42건 통과 후 검색 조건이 빠져 있던 연결 화면
fixture를 수정해 해당 1건만 다시 실행했다. debug APK 빌드도 통과했다.
`ConnectedPlaceBookmarkUiTest`는 API client 대역을 사용하고 실제 운영 Compose 화면과
controller를 연결한다. `connected-saved-390.png`는 지도 SDK를 끈 390dp 렌더다.
실제 회원 API·기기 지도/키보드/회전 확인은 서버 반영 후 남는다.

PR #297의 하단 찜 UI·상태 계약에 #304가 실제 회원별 저장·조회 API를 연결한다.
운영 `ConnectedPlaceSearchScreen`에서 목록·상세 하트와 `찜한 시설` 탭을 제공한다.
`PlaceBookmarkLabActivity`는 합성 시설로 배치를 검토하는 debug 전용 화면으로 남는다.

## 열람과 조건

- 하단 손잡이 영역에 `검색 결과 | 찜한 시설` 탭과 목록/지도 전환을 둔다.
  기존 손잡이 48dp를 56dp로 바꾸지만 시트의 접힌 높이는 유지한다.
- 찜 최초 진입은 검색에 **적용된** 카테고리·이름·중심·반경·반려견·필수 조건을 이어받는다.
  입력 중인 미제출 이름은 검색 탭에 그대로 보관한다.
- `전체 찜 보기`는 카테고리·이름·지역 범위·필수 조건·주차 정렬을 해제하고 반려견 선택을 보존한다.
  먼 지역에 저장한 시설도 조회 대상이며, 이후 찜 안에서 새로 조건을 선택한다.
- 검색 복귀는 이전 조건·입력·시설 선택·카메라·목록 스크롤을 복원한다.
  검색 조건이 같으면 찜 탭의 개별 조건도 유지하고, 검색 조건이 바뀌면 다음 찜 진입 때 새 조건을 이어받는다.
- 지도와 목록은 같은 결과 집합을 받는다. 목록/상세의 하트는 같은 저장 키 집합을 사용한다.
  찜 해제는 서버 성공 후 목록에 반영하며 실행 취소를 제공한다.
- `저장한 시설 없음`, `조건에 맞는 찜 없음`, 불러오는 중, 불러오기 실패를 구분한다.
  저장 총수와 현재 조건의 결과 수를 따로 표시한다.

`PlaceBrowseSession`은 화면 상태 계약이며 저장소나 HTTP 클라이언트가 아니다.
`PlaceBookmarkRepository`가 회원 전체 찜(최대 200개)을 조회한다. 기존 검색 API의 제한된
결과 페이지를 거르지 않는다. 계정별 controller가 회원 전환·재로그인 후의 옛 응답을 버리고,
요청 generation으로 조건 변경 전 응답을 무시한다. 쓰기는 직렬화하며 응답 유실 후에도
목록을 다시 확인할 때까지 하트 상태를 확정하지 않는다. 로컬 디스크 캐시는 두지 않는다.

## 실제 API 연결

`API_BASE_URL`의 `/app/places/bookmarks`에 회원 Bearer로 GET/PUT/DELETE,
`/app/places/bookmarks/search`에 필터를 POST한다. 계약은
[DEV 시설 찜 API](https://github.com/SAJOYO/DAENGS_dev/blob/feat/place-bookmarks/docs/place/bookmarks-api.md)와 맞춘다.
다른 지역·다른 업종의 저장 키까지 전체 조회하며 카테고리·이름·반경·기존 필수 조건으로
새로 거른다. 필수 조건 대화상자에서 기존 조건·OR 조합을 해제할 수 있다.

원본이 사라지거나 병합으로 숨겨진 시설은 저장 이름과 찜 해제 버튼을 표시한다.
위치 기준이 없는 전체 찜에서는 거리를 `거리 미확인`으로 표시한다.
기준 위치가 없는 상태의 반경 선택은 검색 탭에서 위치를 먼저 선택하도록 안내한다.
서버 미반영·인증·한도·통신 실패는 별도 안내하며 실패를 빈 찜으로 취급하지 않는다.

적용은 DEV #438의 basicPostgres 마이그레이션 → Place와 APP backend 배포 → 앱 순서다.
새 앱 키나 환경 변수는 없다. 운영 DB 적용·실제 회원/기기 연동은 로컬 검증과 별개다.

## 상단 공간

`PlaceResultsScaffold`의 검색창 영역 아래 padding을 **12dp → 4dp**로 줄였다.
상단 12dp와 좌우 16dp, 검색 버튼 48dp는 유지한다. 카테고리 버튼 자체를 줄이지 않는다.
320dp 폭에서는 기존 2행 결과 조건 배치를 유지한다.

## 검토 화면 실행

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.daengs.app/.ui.places.lab.PlaceBookmarkLabActivity
```

Activity와 예시 시설·필터링 모델은 debug 소스셋에만 있다.
상단에 `예시 데이터 · 저장 안 됨`을 표시하고 기본/빈 찜/로딩/실패 상태를 전환한다.
기본 지도 영역은 네트워크 없는 시설 선택용 대체 화면이다. `지도 켜기`는 기존 Naver MapHost를
사용하므로 유효한 지도 키와 네트워크가 필요하다. GPS나 실제 회원 API를 호출하지 않는다.
반려견은 예시 이름이며 실제 동반 평가를 재계산하지 않는다. 상태는 메모리 전용이다.

## UI 1단계 확인 범위

대상 테스트는 `PlaceBrowseSessionTest`, `PlaceBookmarkUiTest`,
`PlaceResultsSheetTest`, `ConnectedPlaceSearchUiTest`, `PlaceCardActionsTest`로 제한한다.
새 Compose 테스트는 390dp/320dp 렌더를 `app/build/reports/place-bookmarks`에 남긴다.
실제 지도 타일·카메라 이동, 실기기 키보드/회전/큰 글자, 회원 저장 API 통합은 별도 확인 대상이다.

2026-09-11: 위 5개 클래스의 31개 테스트와 debug APK 빌드를 검증했다.
최초 실행에서 상세 하트 테스트가 배경 목록까지 선택해 실패했고, 상세 시트 내부로
선택자를 한정한 뒤 수정된 `PlaceBookmarkUiTest` 5개를 다시 실행해 통과했다.
기존 검색 UI 22개와 상태 모델 4개는 최초 실행에서 통과했다. 전체 테스트는 실행하지 않았다.
390dp/320dp Compose 렌더 7장을 확인했으며, Naver 지도 영역은 예시 화면으로 대체한 캡처다.
