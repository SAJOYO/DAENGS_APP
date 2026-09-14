# 상세 지도 표현 책임

상세 화면은 상태와 사용자 이벤트를 연결하고, 공용 지도 표현은 아래 파일에서 제공한다.
PR #398은 함수와 Preview의 위치만 옮긴다. 같은 `ui.walk` 패키지와 기존 호출을 유지한다.

| 담당 파일 | 역할 |
| --- | --- |
| DiaryMapNavigation.kt | 카메라 요청, 범위 계산, 장면 선택 최소 확대 기준 |
| DiaryMapPresentation.kt | 장면 번호·선택·그룹 강조를 핀 입력으로 변환하고, 지도 표시용 끝점·머무름 표식을 보정 |
| ObservedRoutePresentation.kt | 관측 경로 표현과 두 종류의 장면 대응 안내 문구, 해당 범례 Preview |
| WalkDiaryMapScreen.kt | 계정별 상세 상태와 지도·서랍·편집기·사용자 이벤트 연결 |

장면 핀 생성은 여전히 장면당 입력 하나를 만든다. 화면 좌표에서의 그룹화와 실제 SDK
오버레이 생성·갱신·해제는 지도 제공자의 책임이다. 표시용 머무름 표식 생략은 원본 경로와
머무름 데이터를 수정하지 않는다.

이동한 함수·상수·Preview와 `WalkDiaryMapForAccount` 본문을 이전 커밋과 대조했다.
새 상태 소유자나 정책 객체를 추가하지 않으며 remember/Effect 키·콜백·서랍 정착점·선택·
재생·저장·계정 경계를 유지한다. 기준값과 문구도 바꾸지 않는다.

기존 `WalkDiaryMapScreenTest`, `DiaryMapNavigationTest`, `RecordContextPresentationTest`,
`MeasurementObservedPresentationTest`, `WalkDetailDataUiTest`, `WalkDiaryCompactDrawerTest`로
직접 호출, 실제 상세 조립, 카메라 유지, 지연된 재조회, 서랍과 재생 동작을 검사한다.
검증 결과는 PR #398에 기록한다. 파일 위치만 확인하는 새 테스트는 추가하지 않는다.
