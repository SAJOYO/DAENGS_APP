# 일반 행동·사진 핀의 수명

`NaverMomentLayer`는 일반 행동/사진/번호 핀의 사진 로딩과 SDK Marker 생성·해제를 소유한다.
`NaverMapSurface`는 표시 대상을 고르고 레이어를 항상 호출한다. 빈 목록이나 아직 준비되지
않은 지도도 그대로 전달해 기존 정리 조건을 유지한다.

- `recordPin`과 `diaryPin`은 기존 `NaverGroupedMomentLayer`로 전달한다.
- 위치 얼굴은 `NaverLocationLayer`, 경로는 `NaverWalkRouteLayer`가 소유한다.
- 지도 생성·파괴, 카메라 이동·복원은 Surface에 남는다.

## 보존한 갱신 조건

| 작업 | 기준 |
| --- | --- |
| 사진 읽기 | 중복을 제거한 사진 파일 목록을 `produceState` 키로 사용 |
| 마커 생성/해제 | 지도, 일반 마커 목록, 사진 아이콘 결과, 밀도, 진단 객체, 마커 globalZ |
| 클릭 콜백 교체 | `rememberUpdatedState`로 현재 콜백을 읽고, 교체만으로 핀을 재생성하지 않음 |

선택/사진 결과가 바뀌면 기존처럼 마커 목록을 해제하고 다시 만든다. 이번 분리는
마커 캐시나 변경분 갱신 최적화를 추가하지 않는다. 사진 로딩도 지도/선택 변경만으로
다시 시작하지 않는다. 원본 파일을 읽지 못하면 기본 핀과 클릭을 유지하며 취소는 전파한다.
아이콘 우선순위는 행동 그림 → 번호 배지 → 사진 → 기본 핀이다.

## 회귀 검사

`NaverMomentLayerTest`는 실제 Compose Effect와 사진 파일 디코딩을 사용하고 네이버의
네이티브 오버레이 호출만 Robolectric shadow로 대체한다.

- 늦게 준비되는 지도, 클릭 콜백 교체, 지도 교체, 화면 이탈의 마커 수명
- 선택/숨김에서 크기·앵커·앞뒤 순서
- 행동 그림과 번호 배지의 우선순위
- 사진 교체 결과와 읽기 실패 후 클릭 가능한 기본 핀

`MapDependencyBoundaryTest`는 main/debug/release의 지도 Kotlin 소스에서 산책 UI의
직접 참조를 막는다. Preview의 완전 수식 호출도 검사 대상이다. #332 이후 Preview가
역참조를 다시 만든 #346 사례를 방지하는 검사이며 전체 의존 그래프 분석기는 아니다.

관련 검사와 빌드 명령:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:compileReleaseKotlin :app:testDebugUnitTest `
  --tests 'com.daengs.app.map.provider.naver.NaverMomentLayerTest' `
  --tests 'com.daengs.app.map.MapDependencyBoundaryTest' `
  --tests 'com.daengs.app.ui.walk.WalkActionMarkerTest' `
  --tests 'com.daengs.app.map.provider.naver.NaverLocationLayerTest' `
  --tests 'com.daengs.app.map.provider.naver.NaverGroupedMomentLayerTest' `
  --tests 'com.daengs.app.map.provider.naver.WalkRouteOverlayStoreTest' `
  --tests 'com.daengs.app.DesignLockTest' `
  --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' `
  '-Dorg.gradle.jvmargs=-Xmx3072m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' `
  -PslimAbi=arm64-v8a --console=plain
```

네이티브 지도 위 가시성·성능을 위 JVM 검사로 확인했다고 간주하지 않는다.
실행 결과와 기기 검증 여부는 PR #394에 기록한다.
