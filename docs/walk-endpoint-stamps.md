# 출발·도착 스탬프

DAENGS_geo 지도 실험실에서 사용자가 확정한 리드줄·개집을 앱에 반영한다. 작은 아이콘의 중심을 좌표에 고정하며, 경로 방향에 따라 회전하거나 점 옆에 걸치지 않는다.

- 출발: 리드줄, 약 50.7×46.7dp. 기록 중 첫 번째 경로 채택 좌표에서 시작한다.
- 도착: 개집, 약 42.7×46.7dp. 완료한 경로의 마지막 좌표에 표시한다. 기록 중에는 도착을 미리 만들지 않는다.
- 기존 12m 근접 묶음은 유지한다. 두 아이콘을 72×36dp의 한 마커로 표시하고 문구는 `출발 · 도착`이다.
- 문구는 `출발` / `도착`만 쓴다. 선택하면 캡션과 기존 경로 상세가 연결되며 아이콘 크기·좌표는 바뀌지 않는다. 라이브 출발점은 캡션만 토글한다.
- `TrailRecorder.startSample`은 첫 채택 fix 한 개만 추가 보존한다. 표시 동선의 5,000점 상한으로 오래된 좌표가 빠져도 출발점이 이동하지 않는다. pause/resume/stop에서도 유지하고 새 산책에서 초기화한다. 원본 저장 형식·거리 계산·GPS 필터는 그대로다.
- `MapScene`에서 허용한 동선에만 출발 스탬프를 붙인다. 완료 레이어가 준비되면 라이브 출발을 대체해 중복을 막는다.

## 그림

로컬 실험실 `tools/gps-lab/map.mjs`의 최종 SVG 경로를 기존 `ic_walk_start`, `ic_walk_finish`, `ic_walk_start_finish` vector drawable로 옮겼다. PNG/WebP 반입이나 새 의존성은 없다. 크기는 drawable의 dp 규격이 원본이며 Naver 마커와 Compose Preview가 같은 intrinsic 크기를 사용한다. 모든 앵커는 `(0.5, 0.5)`이다.

`NaverRouteEndpointLayer`의 Preview로 세 종류의 에셋을 확인할 수 있다. 지도에서의 크기·겹침·탭은 실기기 확인이 필요하다. 이번 작업은 기기에 설치하지 않았다.

## 검증 범위

`TrailRecorderTest`, `TrailLayerStateTest`, `RouteEndpointStampsTest`, `WalkCompletedRoutePresentationTest`, `MapScenePolicyTest`와 Debug 빌드를 대상으로 한다. 첫 유효 표시점·trim·pause·새 산책 초기화, 라이브→완료 대체, 지도 목적별 숨김, 단순 문구, 기존 근접 묶음을 확인한다.

시간 포맷 인자 제거로 호출이 바뀐 `WalkViewModelTest`와 `WalkDiaryPhotoUiTest`의 사진 위치 유지 항목도 대상에 포함한다. 전체 스위트는 실행하지 않는다.

결과: Debug 빌드 성공, 대상 테스트 49개 통과.
