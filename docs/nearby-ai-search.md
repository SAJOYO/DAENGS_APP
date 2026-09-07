# 내 주변 AI 조건 검색

개발용 `ConnectedPlaceSearchScreen`에서 AI 모드로 문장을 제출하면
`POST /app/places/discovery`를 호출한다. 기존 `DaengsApp.sessionProvider`에서
로그인 세션을 받아 Bearer 토큰을 사용한다. 서버 주소는 기존 `daengs.apiBaseUrl`이다.
카카오 REST 키는 앱 로그인용 네이티브 키를 대신할 수 없다.

## 화면 흐름

1. 현재 검색 위치·반경·카테고리·주차 선호·선택한 반려견 스냅샷을 보낸다.
   전체 카테고리는 빈 `kinds`로 보내 수동 업종 제한을 걸지 않는다.
2. 서버가 해석한 검색 방향과 `#조건`을 확인한다. `needs_selection` 조건의
   `proxy` 선택지만 실행할 수 있고, 대체 기준이라는 설명과 미지원 사유를 함께 표시한다.
3. 조건 선택은 `POST /app/places/discovery/actions`의 `refine`, 방향 확정은 `confirm`이다.
   서버의 `search_id`·`revision`·선택 ID를 보내며 앱이 계획이나 LLM 프롬프트를 만들지 않는다.
4. 확정된 lens의 검색 응답을 지도·카드에 표시한다. 일반 검색을 다시 호출하면 AI 조건이
   사라지므로 확정 이후 `/v2/places/search`로 재검색하지 않는다. 서버의 설명·주의·출처 기반
   표시 문구는 카드 상세에서 그대로 보여준다.

## 상태와 검증

- 계정·반려견·위치·반경·카테고리·주차 조건이 바뀌면 continuation을 폐기하고 다시 제출한다.
- 새 검색·화면 이탈은 진행 중 요청을 취소하며, 늦은 응답은 generation으로 버린다.
- 액션 통신 실패 후 재시도는 같은 `client_request_id`와 revision을 사용한다.
  409·410·422는 이전 continuation을 버리고 새 검색으로 복구한다.
- 요청 echo·반려견 echo·업종 범위·장소/설명 ID·액션 echo가 맞아야 결과를 표시한다.
  로그아웃이나 세션 교체가 요청 도중 발생해도 이전 계정 결과를 받지 않는다.
- 사용자에게 표시하는 지원 여부는 서버 응답을 따른다. 예를 들어 ‘싼’의 거리 대체 기준을
  실제 가격 검색으로 표현하지 않는다.

대상 테스트는 `FacilityApiTest`, `FacilitySearchCoordinatorTest`, `FacilityViewModelTest`,
`FacilitySearchPanelTest`, `FacilityConnectedUiTest`다. 화면 테스트는 저장된 장소 응답으로
렌더링한다. `DAENGS_UI_CAPTURE_DIR` 환경 변수를 지정하면 해석/확정 화면 PNG를 그 경로에
저장한다. 이 이미지는 실서버 LLM 실행 증거가 아니다.

실서버 검증에는 로그인 가능한 네이티브 앱 키와 회원 세션이 필요하다. 에뮬레이터의 가상
위치가 거부되면 지도에서 이동한 뒤 ‘이 지역 검색’으로 기준 위치를 명시할 수 있다.
출시용 기존 `PlacesScreen`의 진입 정책과 채팅 시설 카드는 이번 연결에서 바꾸지 않는다.
