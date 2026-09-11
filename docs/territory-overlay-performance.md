# 전봇대 객체 재사용 검증

## 범위

지도별 `TerritoryOverlayStore`가 ID별 본체를 보관한다. 화면에 전달되는 표시 상태가 바뀌면 새 ID만 생성하고 사라진 ID만 제거한다. 기존 ID는 실제 표시가 달라진 속성만 SDK에 전달한다. 선택·범위 원·성공 발자국은 각각 수명을 가진다. 지도 교체 또는 화면 이탈 시 전체 객체를 정리한다.

`ready`, 선택되지 않은 장소의 상세 문구, 범위 원이 없는 장소의 근접 구간은 표시 비교에서 제외한다. 인증·회원 소유 색, 알 수 없는 점유의 중립 표시, 선택 시 충돌 숨김 해제는 유지한다. 실제 점령 반경이나 API는 바꾸지 않는다.

효과의 전체 목록 순회는 후속 2단계에 남아 있다. 이번에는 재사용한 객체에 동일 프레임 속성을 다시 쓰지 않도록 하고, 취소·선택 전환 시 기본 프레임으로 복원한다. 이미지 합성·캐시·해상도, 누적 경로는 바꾸지 않는다.

## 재현

Debug 전용 `TerritoryPerformanceLabActivity`는 가상 장소 50·200·500개를 같은 격자에 만든다. 자동 비교는 초기 지도 준비 후 각 개수에서 선택 20회(250ms 간격), ready 20회(100ms), 점유 10회(200ms), 성공 효과와 해제를 실행한다. 직접 전봇대·바닥 탭도 가능하다. 뒤로 닫아 Activity가 파괴되면 자동 실행은 취소된다. 비교 중에는 이 화면을 전면에 유지한다. 실제 위치·회원·점령·북마크 API는 호출하지 않는다.

```powershell
adb shell am start -W -n com.daengs.app/com.daengs.app.ui.walk.TerritoryPerformanceLabActivity --ez auto true
adb logcat -d -s TerritoryPerf:I '*:S'
```

별도 설치용 `.cardpreview` APK에서는 component의 앞 패키지만 `com.daengs.app.cardpreview`로 바꾼다. 동일 Activity에서 새 자동 실행을 시작하려면 이전 실행이 끝난 후 화면의 자동 비교 버튼을 사용한다.

`create/remove`는 전봇대 본체 생성·제거 수, `update`는 표시 변경을 받은 기존 장소 수다. 범위 원·발자국 수나 실제 GPU draw call 수가 아니다. `syncMs`는 목록 반영 CPU 시간의 합이며 지도 비동기 렌더링 완료 시간은 아니다. 초기 생성·화면 정리도 수집하지만 자동 비교의 각 구간 시작 때 누적값을 초기화한다. 원본 비교 계측에서 제거 비용은 syncMs 밖이므로 절대 시간 개선율로 일반화하지 않는다.

`FrameMetrics.TOTAL_DURATION`은 Activity 창의 UI 프레임만 수집한다. 별도 지도 렌더링 프레임을 모두 포괄하지 않는다. samples 0의 p95 0은 지연이 0이라는 뜻이 아니라 **측정 자료 없음**이다. 실제 FPS·지도 끊김 판단에는 Perfetto 등 별도 렌더링 추적이 필요하다. Debug 빌드의 단일 반복 결과를 release 성능 보장으로 사용하지 않는다.

## 대상 테스트

`TerritoryOverlayStoreTest`는 실제 재사용 store를 SDK handle 대역으로 실행한다. 500개 선택 전환, 표시와 무관한 상태, 회원 소유/인증/알 수 없음, 순서 변경/추가/제거/이중 clear/재진입, 범위·좌표·해제, 효과 교체·취소를 검증한다. 네이티브 SDK 그림·클릭·원 정리는 실기기에서 별도로 확인한다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.provider.naver.TerritoryOverlayStoreTest' --tests 'com.daengs.app.map.layers.territory.TerritoryPoleArtTest' --tests 'com.daengs.app.ui.walk.TerritoryBoardPresentationTest' :app:assembleDebug -PslimAbi=arm64-v8a --max-workers=2 --console=plain
```


## 2026-09-11 실기기 결과

기준 dev `4d5f6929`의 기존 전봇대 레이어에 동일 probe와 Debug 측정 화면을 추가한 APK와 재사용 구현 APK를 같은 기기에 순서대로 설치했다. 두 번 모두 설치 Success와 기기에서 회수한 APK의 SHA-256 일치를 확인했다. 단일 실행이며 워밍업 후 선택 20회의 결과다.

| 전봇대 수 | 기존 생성 / 제거 | 변경 후 생성 / 제거 | 변경 후 기존 장소 갱신 | 기존 syncMs 합 | 변경 후 syncMs 합 |
| --- | --- | --- | --- | --- | --- |
| 50 | 1,000 / 1,000 | 0 / 0 | 39 | 61.21 | 9.70 |
| 200 | 4,000 / 4,000 | 0 / 0 | 39 | 126.17 | 21.84 |
| 500 | 10,000 / 10,000 | 0 / 0 | 39 | 228.84 | 47.87 |

39회는 최초 선택 1회 + 이후 선택 전환 19회의 이전/새 대상 각 1회다. 세 규모 모두 ready 20회 변경은 생성·제거·갱신 0회, 점유 10회 변경은 기존 장소 갱신 10회였다. 효과 시작·해제도 본체 재생성 없이 기존 장소만 2회 갱신했다. 효과 중 전체 목록 순회 자체는 남아 있다.

UI frame sample은 대부분 0개, 점유 구간은 1개뿐이어서 p95/FPS 개선 판단에 사용할 수 없다. 위 결과는 불필요한 본체 재생성 제거와 동기 갱신 작업 시간의 관측 결과다. 실사용 산책의 프레임 개선율, GPS·네트워크·장시간 경로 성능은 측정하지 않았다.

대상 테스트는 21개(재사용 6, 이미지 6, 지도 매핑 9), 실패·오류·skip 0. 별도 미리보기 APK 빌드 성공. 실기기에서 네 가지 색상·인증 체크를 확인하고 내 인증 전봇대 선택 후 바닥 탭으로 범위와 보조 문구가 사라짐을 확인했다. 테스트의 SDK 대역은 실제 지도 그리기를 검증하지 않는다.
