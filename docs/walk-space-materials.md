# 정규화된 산책 공간 재료 수신

분류 사전과 정책은 [DEV #474](https://github.com/SAJOYO/DAENGS_dev/pull/474)의
`daengs_walk.diary_space_*`에 있다. APP은 상권·공원·피복을 다시 분류하지 않는다.

미리보기 요청에 `collect_backgrounds=true`를 넣어 서버의 정규화 수집을 사용한다.
`space-material-v1`의 `material` 문구와 `relation`을 표시한다. 상권 조회 반경·가장 가까운
등록 업소 거리, 공원 등록 지점 거리, 조회점의 피복 적용 범위를 구별한다.
등록 지점을 공원 내부로, 상권의 분포 중심을 사용자 위치로 바꾸지 않는다.

일기 발행은 기존 `walk-diary-board-v1` 응답을 사용한다. 서버의 작성 결과가
`WalkDiarySync → WalkSceneAnalysisRow → WalkDiaryReader`로 전달되므로 새 Room 필드나
장면 재분류를 만들지 않는다. 행동핀 없는 관측·경계 장면도 그대로 저장한다.

`DiarySlotEvidenceTextTest`는 의미·거리 표시를, `DiarySlotPreviewTest`는 실제 HTTP 요청을
검사한다. `WalkDiarySpacePersistenceTest`는 DEV의 합성 HTTP 응답을 사용해 생성 요청 후
Room에 저장하고 DB를 닫았다 다시 열어 상세 읽기의 본문·장면 수를 대조한다.
fixture `storyboard/diary-space-board-v1.json`은 DEV의
`backend/evals/walk-diary/space-board-v1.json`과 같은 바이트다. writer는 테스트 대역이며
Gemini 문장 품질을 검사한 자료가 아니다.

미리보기의 새 요청 필드를 허용하는 DEV #474를 먼저 적용한다. 운영 활성화와
실기기 설치는 이 코드 검증과 별도다.
