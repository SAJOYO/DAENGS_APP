# 산책 경로 선 재사용

`NaverMapSurface`의 경로 처리를 `NaverWalkRouteLayer`와 지도 생애에 속한 `WalkRouteOverlayStore`로 분리했다. 원본 좌표, 기록 필터, 거리 계산, 속도색 정책은 변경하지 않았다. 최신 dev `4dfb0044`의 14px 경로 폭과 2px 흰 테두리를 유지한다.

## 갱신 경계

- 라이브/완료 구분, 기록 구간 순번, 속도 경로/좌표 전용 종류를 키로 쓴다. 서로 떨어진 구간을 한 선으로 연결하지 않는다.
- 입력 구간과 정책/테마가 같으면 가공된 색상 파트를 그대로 재사용한다. 상위 계층이 같은 내용을 새 List로 만들어도 내용이 같으면 다시 가공하지 않는다.
- 변경된 구간은 기존 속도 보간 함수로 전체 가공하고, 기존 SDK 선에 변경분을 반영한다. 좌표가 같고 색상만 달라지면 좌표 변환/설정을 생략한다.
- 구간이 새로 생기거나 종류가 바뀔 때만 선을 생성한다. 제거, 2점 미만, 그릴 수 없는 0길이 구간은 선을 해제한다. 지도 해제 시 객체와 캐시를 모두 비운다.
- 완료 경로 강조에 따른 흐리기는 완료 속도 경로의 색상만 바꾼다. 좌표 전용 경로는 기존의 속도 미상 색상/흰 테두리를 유지한다.
- 중첩 List와 정책은 불변 snapshot 계약이다. 같은 List를 직접 수정해 전달하는 방식은 지원하지 않는다. 순번이 바뀌는 앞쪽 구간 삭제/교체는 해당 순번의 내용을 갱신하므로 과거 좌표가 남지 않는다.

지도에 붙이는 좌표 파트는 각 2점 이상이며 색상 파트와 개수가 일치해야 한다. SDK 속성 갱신은 Compose SideEffect에서 UI 스레드로 실행한다. [네이버 multipart 경로 문서](https://navermaps.github.io/android-map-sdk/guide-ko/5-7.html)와 사용 중인 SDK 3.23.3의 API를 확인했다.

## 실기기 측정 (2026-09-11)

SM-S931N, Android API 36, 별도 `com.daengs.app.cardpreview` debug APK. 가상 경로 총 1천/5천/1만 좌표를 4개 구간으로 나누고 마지막 구간에만 250ms 간격으로 20개 좌표를 추가했다. 초기 지도 로딩 4초와 각 규모 설정 후 2초는 집계에서 제외했다. 폰 잠금 해제 후 다시 실행한 기준 결과와 변경 결과를 비교했다. GPS·서버·실제 산책 기록은 사용하지 않는다.

| 초기 좌표 수 | 기존 생성/제거 | 변경 생성/제거 | 변경 갱신 | 가공 구간 기존→변경 | 가공 좌표 기존→변경 |
| --- | --- | --- | --- | --- | --- |
| 1,000 | 80 / 80 | 0 / 0 | 20 | 80 → 20 | 20,210 → 5,210 |
| 5,000 | 80 / 80 | 0 / 0 | 20 | 80 → 20 | 100,210 → 25,210 |
| 10,000 | 80 / 80 | 0 / 0 | 20 | 80 → 20 | 200,210 → 50,210 |

아래는 20회 합계 관측값(ms)이다. prepare는 속도색 가공, sdk는 좌표 변환 및 SDK 선 생성·해제·갱신 함수에 쓴 시간이다. 내용 동등성 비교, 상위 snapshot 생성, SDK 내부 렌더링과 화면 전체 프레임 시간은 포함하지 않는다. JIT/기기 상태를 통제한 통계적 벤치마크가 아니며 FPS 개선율로 해석하지 않는다.

| 초기 좌표 수 | 가공 기존→변경(ms) | SDK 처리 기존→변경(ms) |
| --- | --- | --- |
| 1,000 | 259.97 → 93.48 | 147.21 → 68.92 |
| 5,000 | 399.77 → 247.38 | 354.59 → 168.28 |
| 10,000 | 509.05 → 243.87 | 541.36 → 256.93 |

라이브 `TrailRecorder`의 기본 표시 보관 한도는 5,000점이다. 10,000점은 더 큰 입력의 스트레스 조건이며 한도를 늘린 것이 아니다.

- 기준 APK SHA-256: `90f6be6bb3f830d41c467f04516a165c170a41d7d098d13c612e312d0e96b5cb`.
- 변경 APK SHA-256: `3744ebac9f2c0ec768542f5d0dd91d820fd7bd05241573cd6e7b4652edfe195c`.
- 각각 adb install Success, 기기에서 회수한 APK와 SHA-256 일치. 파일 크기는 우연히 둘 다 97,287,472 bytes이므로 설치 판별에 쓰지 않았다.
- 실제 지도에서 전후 색상/흰 테두리/4구간 위치, 다섯 번째 단절 구간 추가, 전체 숨김과 복원 확인. 실행 중 앱 크래시 로그 없음.

## 재현과 테스트

`WalkRoutePerformanceLabActivity`는 debug 전용이며 실제 GPS/기록/계정 작업을 하지 않는다. 자동 비교 중 수동 버튼은 비활성화한다.

```powershell
adb shell am start -S -W -n com.daengs.app.cardpreview/com.daengs.app.ui.walk.WalkRoutePerformanceLabActivity --ez auto true
adb logcat -d -s WalkRoutePerf:I '*:S'
```

각 실행의 pid로 logcat을 필터링하고 `DONE`을 확인한다. 화면의 좌표 추가/끊긴 구간/숨김·표시 버튼으로 생애 전환을 확인할 수 있다. 일반 debug 패키지는 component 앞 패키지명을 해당 설치에 맞춘다.

대상 테스트 20개, 실패/오류/skip 0. 전체 suite는 실행하지 않았다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests com.daengs.app.map.provider.naver.WalkRouteOverlayStoreTest --tests com.daengs.app.map.style.WalkStylePolicyTest --tests com.daengs.app.map.layers.trail.TrailLayerStateTest :app:assembleDebug -PslimAbi=arm64-v8a --max-workers=2 --console=plain
```

별도 설치 APK에는 `-I <preview.init.gradle>`로 `.cardpreview` applicationIdSuffix와 별도 Kakao scheme을 적용했다.

- 재사용 11개: 1만 점 마지막 구간 추가, 동일/복사 snapshot, 정지·GPS 단절, 라이브/완료 분리와 흐리기, 테마/정책 변경, 구간 교체/축소, 빈 구간 복원, 좌표 전용 전환, 0거리/잘못된 시각, 지도 해제, 추가 시 직전 edge 보간의 기준 함수 일치.
- 기존 속도색 정책 4개, 구간 상태 투영 5개.
- SDK 대역 테스트는 객체 생애와 표시 데이터를 검증한다. 실제 네이티브 선 표시/추가 좌표 반영은 위 실기기 비교로 확인했다.

## 남은 비용

현재 늘어나는 구간은 전체를 가공/좌표 갱신한다. 구간이 하나뿐인 긴 경로는 가공량 감소가 작고, 선 객체 재사용 효과가 중심이다. 속도 보간은 다음 edge도 참조하므로 단순히 끝에 색상 파트를 붙이면 직전 색이 어긋난다. 필요하면 별도 작업에서 끝부분 재계산을 설계한다. 상위 TrailSnapshot→지도 상태의 전체 좌표 투영과 복사도 이번 범위에는 포함하지 않았다.
