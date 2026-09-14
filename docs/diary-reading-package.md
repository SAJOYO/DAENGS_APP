# 일기 표시 패키지

`ui.walk.reading`은 전달받은 값과 콜백으로 그리는 일기 표시 컴포넌트를 모은다.
상세 화면과 탐색 패널이 이 패키지를 사용한다. reading은 상위 `ui.walk` 화면이나
store/sync/detail 작업 계층을 직접 참조하지 않는다.

| 파일 | 역할 |
| --- | --- |
| DiaryReadingChrome | 승인된 여백·글자 크기, 선택·접기 상태를 전달받는 탭 |
| DiaryReadingHeader | 제목과 해당 산책에 참여한 강아지 표시 |
| DiaryReadingNotices | 준비·방향·오류 문구와 사용자 액션 표시 |
| DiarySceneText | 편집기와 동일한 본문 표시 |
| DiarySceneFooter | 편집·삭제·이전·다음 콜백 표시 |
| DiarySceneExploreActions | 구간 복귀·주변 탐색 콜백 표시 |
| DiaryReviewTheme | Preview/테스트의 요청 글자 배율 유지 |

7개 파일은 package 선언만 변경한다. 기존 internal 가시성, 함수 본문·기본값·문구·Preview를
유지하며 호출부에는 명시적 import만 추가한다. Gradle 모듈과 상태 소유자를 추가하지 않는다.

사진 로딩, 장면 메뉴, 서랍 상태, 읽기와 탐색 상태의 상호 연결은 이번 범위에 포함하지 않는다.
`DiaryReadingBoundaryTest`는 새 패키지 전체의 직접 역참조를 검사한다. Kotlin internal은
모듈 단위이므로 이 검사는 별도 모듈 격리를 뜻하지 않는다. 기존 표시·상세·서랍·탐색·디자인
검사를 사용하며, 실행 범위와 결과는 PR #399에 기록한다.
