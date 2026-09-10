# 서버 기본 보드를 같은 장면으로 읽기

DEV `walk-diary-board-v1`을 기존 카드·지도·연필 편집기에 연결한다.
새 화면이나 Room 테이블은 추가하지 않는다.
서버 계약 카드는 [DAENGS_dev#424](https://github.com/SAJOYO/DAENGS_dev/pull/424)이다.

## 한 장면, 한 본문

`ServerDiaryBoard`는 네 원천 종류(사용자 기록, 움직임 관측, 동선 지점, 시작/종료)를
기존 `StoryboardScene`으로 읽는다. `title`과 `body`가 사용자가 보는 장면이다.
`SceneBodyScope.SCENE`이므로 원본 메모·행동 문구를 다시 덧붙이지 않는다.
직접 남긴 기록·위치 근거·생성 실패를 별도 본문 블록으로 붙이지 않는다.

기존 `saveDiarySceneEdit`가 전체 본문을 저장한다. 빈 본문으로 고친 선택, 제목,
숨김 상태도 같은 draft에 보존된다. 새로고침은 저장된 결과를 다시 읽으며
새 형식에 대해 AI 재생성 POST를 보내지 않는다.

## 지도와 순서

- 사용자 기록: 원본 기록·사진 ID를 기존 `entry:`/`photo:` 장면 ID로 연결한다.
- 동선 지점·움직임·위치 있는 출발/도착: 서버의 client_seq, chain_index, 관측 시각을
  로컬 원본 GPS와 비교한다. 일치하지 않으면 카드만 보여준다.
- GPS 없는 출발/도착: 위치 없는 카드다. 지도 중심이나 최근 좌표로 핀을 만들지 않는다.
- 장면 순서는 서버 순서를 유지한다. 동일 위치의 장면도 서로 다른 카드다.

## 호환성

capabilities가 새 형식을 제공하면 우선 선택하고, 기존 v1만 제공하면 기존 동작을
사용한다. 일기 기능이 없는 서버에는 기존 스토리보드 동기화를 사용한다.
새 형식 요청에 이미 저장된 v1 또는 legacy 응답이 돌아오면 그 형식 그대로 보관한다.
통신 오류나 새 형식 파싱 실패를 구형 생성으로 우회하지 않는다.

계정·원본 revision·사진 manifest가 요청 중 바뀌면 결과를 채택하지 않는다.
Room의 생성 결과와 사용자 draft는 기존처럼 따로 저장된다.

## 범위와 확인 자료

이 문서는 기본 보드 소비 연결(#273)을 설명한다. 종료 직후 준비, 전체 10초 예산과
완성된 보드만 공개하는 후속 정책은 [단일 공개](walk-diary-publication.md)에 있다.

DEV가 합성 동선으로 생성한 `backend/evals/walk-diary/board-v1.json`을
`app/src/test/resources/storyboard/diary-board-v1.json`에 그대로 보존한다.
실제 모델 호출이 아닌 고정 응답으로 계약·본문·지도 앵커를 검증한다.
`ServerDiaryBoardTest`와 `WalkDiaryBoardSyncTest`는 이 응답을 사용한다.
기존 일기 파서·본문·리더·동기화 테스트도 관련 회귀 범위에 포함한다.
