# 원본에 따른 장면 표시 (#377)

산책 상세 UI 개선의 1단계다. `DiarySceneKind`가 원본 종류를 판정하고, `DiarySceneBadge`와
`DiarySceneHeading`/`DiarySceneListButton`이 목록·본문·시간 범위의 장면 목록에 같은 표시를 제공한다.

## 판정 계약

- `WalkDiaryMapForAccount`는 **동일한 `DiaryWalk`의 `sourceEntries`**로 종류를 계산한다.
  별도로 갱신되는 `WalkDetailSource.entries`를 합치지 않는다. 장소 설명 비교나 사용자 제목 수정은 종류를 바꾸지 않는다.
- `entryId` 또는 구조화된 entry 참조가 있으면 같은 세션·ID의 유일한 원본을 찾는다.
  참조에 revision/반려견/메모 여부가 있으면 대조한다. 참조 충돌, 원본 누락·중복, 검토 필요,
  숨김/사용 불가 원본, 기록 종류 충돌은 공통 장면으로 표시한다.
- entry 참조가 없는 경우 같은 세션의 실제 사진, 검증된 `recordKind`를 사용할 수 있다.
  서버 사진은 이 기기에 파일이 없어도 사진 종류를 유지한다. 파일 유무 안내는 기존 UI가 담당한다.
- `recordKind=behavior`만 있고 entry가 없으면 세부 행동을 추측하지 않는다.
  제목·본문·장소명·GPS 속도·목록 순서로 물 마시기, 귀가, 달리기 등을 판정하지 않는다.

| 원본 | 표시 | 그림 |
| --- | --- | --- |
| SNIFFING / EXCRETION / BARKING | 킁킁 / 배설 / 짖기 기록 | 지도 액션 핀의 기존 `ic_walk_*` |
| NOTE / note | 직접 남긴 메모 | 기존 `ic_walk_note` |
| 사진 / photo | 사진 기록 | 공통 Camera |
| observed_dwell | 머무른 구간 | 공통 Clock |
| observed_fast / observed_slow | 이동이 빨라진 / 느려진 구간 | 공통 Chart, 서로 다른 문구 |
| 근거 없음·알 수 없는 종류 | 산책 장면 | 공통 Book |

관측 타입은 기기 이동 기록이며 강아지의 행동 추론이 아니다. 사진·관측·공통 배지는 기존
중립 테마 바탕, 행동 배지는 크림 바탕, 메모 배지는 연분홍 바탕을 쓴다. HTML의 새 색을
화면에 하드코딩하거나 Success/Warning/Error를 장식 색으로 쓰지 않는다.

장면 번호는 목록 순서와 지도 대응을 나타낸다. 종류 배지와 분리하며, 공백의 회색 `–`는
기존대로 장면 번호에 포함하지 않는다. 제목 아래 시각 하나와 종류 문구를 표시한다.
서랍·카메라·재생 상태 구조는 기존 구현을 사용한다. 상세 전체 재배치는 후속 단계다.

## 검증

2026-09-13 Windows JVM/Robolectric에서 대상 10개 클래스 58개 검증 후,
같은 스냅샷만 사용하는 실제 화면 통합 사례를 추가하고 `DiaryScenePresentationTest` 4개를 다시 확인했다.
중복을 제외한 검증 사례는 총 59개, 실패/skip은 없다. `:app:assembleDebug`도 통과했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*DiarySceneKindTest' --tests '*DiaryScenePresentationTest' --tests '*DiarySceneRemovalUiTest' --tests '*WalkDiaryCompactDrawerTest' --tests '*MeasurementTimeControlsTest' --tests '*DesignLockTest' --tests '*WalkDiaryMapScreenTest' --tests '*WalkDiaryReadViewTest' --tests '*WalkDiaryPhotoUiTest' --tests '*WalkDiaryGapListTest' :app:assembleDebug
```

원본/문구 독립성, 원본 참조 revision·반려견·세션·중복, 사진 파일 없음, 지원하지 않는 타입,
실제 화면의 원본 종류만 갱신되는 경우, 목록/본문/시간 범위 표시와 클릭, 삭제와 3단 서랍,
기존 지도 선택·사진·공백을 확인했다. 기존 잠금 테스트는 수정하지 않았다.

공통 부품은 320dp/큰 글꼴 Preview를 포함한다. 테스트 실행 전 `DAENGS_SCENE_PREVIEW_DIR`을
설정하면 합성 자료만 있는 종류 목록과 상세를 native graphics로 PNG 렌더한다.
이는 실기기 Naver 지도·서버 검증이 아니다. 이번 단계에는 휴대폰 설치나 새 패키지 추가가 없다.
