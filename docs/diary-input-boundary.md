# 순수 일기 입력과 저장 어댑터

일기 조립·분석 표시·로컬 일기 생성은 `walk.store`의 Room 행을 직접 받지 않는다.
`DiaryInputMapping.kt`가 이미 읽은 행을 변환하며 새 조회, 작업, 캐시를 만들지 않는다.

```text
Reader / 기록 조회 / 기존 스토리보드 화면 / DAO
    → walk.store.DiaryInputMapping
        → walk.diary의 값과 순수 계산
```

| 입력 | 필요한 값과 용도 |
| --- | --- |
| StoryboardAnalysisInput | 최신 시도 stamp·상태, 마지막 성공 bundle·stamp |
| DiaryBoardSource | 분석 표시 결과와 발행된 원본. 제목만 읽을 때 사용 |
| DiaryBoardInput | 위 원본과 변환된 행동 기록, 현재 사진 ID. 본문 조립에 사용 |
| DiaryPhotoInput | 사진 ID·촬영 시각·좌표. 파일이나 업로드 상태 없이 로컬 장면 생성 |

## 보존하는 계약

- 제목 경로는 행동 본문을 파싱하거나 발행 원본과 사용자 편집을 병합하지 않는다.
  발행 행이 있으면 준비 중인 경우까지 기존 분석보다 우선하며, 사용하지 않는 분석 입력의
  stamp도 계산하지 않는다. 저장된 발행 bundle이 손상되면 기존처럼 실패한다.
- 본문 조립은 기존 `WalkEntryRow.entry()` 변환을 사용한다. 삭제 표식은 표시할 기록에서만
  빠지고 stamp 입력에는 남는다. 동기화 진행·오류·revision/mutationId도 그대로 전달한다.
- `storyboardEntryStamp`와 `diaryInputStamp`는 기존 버전과 직렬화 그대로다. stamp 선택 기준은
  최신 시도의 stamp가 아니라 마지막 성공 원본의 `bundleEntryStamp` 접두사다.
- 준비 중 장면 숨김, 발행된 문장 보존, 명시적 행동 수정·삭제와 사진 삭제의 반영 순서는 유지한다.
- 사진 장면의 ID·촬영 시각·좌표·문장·정렬 및 원본 JSON 형식은 유지한다.
- Reader의 계정/완료 세션 검사, Flow 결합 및 IO dispatcher와 publication 작업 수명은 유지한다.
- `saveDiarySceneEdit`의 최신 초안 병합, `saveEntryChecked`의 동시 수정 검사,
  `acceptSceneAnalysis`의 입력 stamp·generation 검사는 원래 DAO 트랜잭션 안에 남긴다.
  DAO 변경은 로컬 사진 입력 변환과 분석 표시 어댑터 호출 두 곳뿐이다.

## 범위와 검증

`WalkDiaryReader`, `WalkDiaryPublication`, 네트워크 API와 미리보기 로더는 저장·통신 어댑터다.
이 파일들을 포함한 일기 패키지 전체를 순수 계층으로 바꾼 것은 아니다. Gradle 모듈과 DB
스키마, 화면 상태 소유권도 바꾸지 않는다.

`DiaryInputBoundaryTest`는 이번에 분리한 입력·조립·분석 표시·로컬 일기 생성 파일에
store/sync/Room 역참조가 다시 들어오지 않는지 검사한다. `DiaryInputMappingTest`는 삭제 표식과
동기화 메타데이터, 발행 원본의 우선순위, 사진 장면의 정보 보존을 검사한다.
기존 조립 테스트의 기대값은 유지하고 저장 어댑터를 통해 입력하는 부분만 갱신했다.

관련 조립·Reader·발행·분석 동기화·기록 검색·편집 충돌·스토리보드 화면의 선택 테스트와
Debug Kotlin 컴파일 결과는 PR #397에 기록한다.
