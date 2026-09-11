# 전봇대 객체 재사용 검증

## 범위

지도별 `TerritoryOverlayStore`가 ID별 본체를 보관한다. 화면에 전달되는 표시 상태가 바뀌면 새 ID만 생성하고 사라진 ID만 제거한다. 기존 ID는 실제 표시가 달라진 속성만 SDK에 전달한다. 선택·범위 원·성공 발자국은 각각 수명을 가진다. 지도 교체 또는 화면 이탈 시 전체 객체를 정리한다.

`ready`, 선택되지 않은 장소의 상세 문구, 범위 원이 없는 장소의 근접 구간은 표시 비교에서 제외한다. 인증·회원 소유 색, 알 수 없는 점유의 중립 표시, 선택 시 충돌 숨김 해제는 유지한다. 실제 점령 반경이나 API는 바꾸지 않는다.

효과는 현재 대상 ID로 직접 조회한다. 이전 대상은 종료·취소·전환 시 한 번 복원한다. sync에서 범위 원·발자국 표시가 바뀐 ID만 별도로 모아 초기화하고, 현재 효과 대상이면 기본 프레임 대신 진행 중인 프레임을 적용한다. 변경 없는 프레임에는 목록을 순회하지 않는다. 같은 불변 표시 목록은 동일 참조로 유지하므로 sync의 전체 내용 비교도 건너뛴다. 이미지 합성·캐시·해상도, 누적 경로는 바꾸지 않는다.

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

## 2단계: 효과 대상 갱신

`framePasses`는 store의 frame 호출 횟수, `handleFrames`는 그 안에서 실제로 전달한 handle.frame 호출 횟수다. `maxFrameTargets`는 한 호출에서 처리한 최대 대상 수다. SDK 속성 쓰기·GPU draw call·디스플레이 FPS를 뜻하지 않는다.

변경 없는 정상 효과 프레임은 1개, 효과 없는 프레임은 0개를 처리한다. A→B 전환은 A 복원과 B 효과로 2개를 처리할 수 있고, 같은 순간 다른 장소의 범위 원이 새로 표시되면 해당 장소의 초기화가 추가된다. 모든 경우에 무조건 1개 이하라는 정책은 아니다.

현재 효과 대상 탐색은 `remember(sites)`로 캐시한다. store에는 불변 표시 snapshot만 전달하며 같은 인스턴스를 애니메이션 동안 유지한다. 일반 장소 목록이 달라졌을 때는 여전히 목록 diff가 필요하다. 수정 중인 가변 List를 같은 참조로 전달하는 방식은 지원하지 않는다.

자연 종료(progress=1)·취소(null)·잘못된 진행률은 이전 대상만 한 번 기본 프레임으로 돌린다. 제거된 대상은 해제된 handle에 접근하지 않으며, 동일 ID가 다시 등장하면 새 객체로 현재 상태를 표시한다. 지도 clear는 이전 효과 ID와 초기화 대기 목록도 비운다.

대상 테스트 명령:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.provider.naver.TerritoryOverlayStoreTest' --tests 'com.daengs.app.ui.walk.TerritoryFeedbackUiTest' :app:assembleDebug -PslimAbi=arm64-v8a --max-workers=2 --console=plain
```

새 store 시나리오는 500개 중 1개 효과 대상만 60회 갱신, idle 처리 0회, 자연 종료/유효하지 않은 진행률의 단일 복원, A→B 전환, 비활성 원 초기화/활성 원의 pulse 유지, 삭제/clear/동일 ID 재등장, 안정된 목록을 60프레임 동안 읽지 않음을 검증한다. 실제 SDK 속성 처리는 handle 대역 바깥이므로 실기기 비교를 함께 기록한다.

### 2단계 실기기 결과 (2026-09-11)

최신 dev d5ad7c7a와 PR #318을 합친 기준 코드에 같은 frame probe를 넣은 APK와 대상 조회 APK를 비교했다. 각각 설치 Success와 회수 APK SHA-256 일치를 확인했다. 기준 APK와 변경 APK의 해시는 서로 다르다.

| 가상 전봇대 수 | 기준 효과 처리 1회당 최대 대상 | 변경 후 최대 대상 | 변경 후 효과 구간 framePasses / handleFrames |
| --- | --- | --- | --- |
| 50 | 50 | 1 | 133 / 133 |
| 200 | 200 | 1 | 133 / 133 |
| 500 | 500 | 1 | 133 / 133 |

변경 후 ready 20회와 점유 10회 구간의 handleFrames는 모든 규모에서 0이었다. 선택 20회는 범위 원 변경 대상에만 총 39회, 한 번에 최대 2개를 처리했다. 생성·삭제는 모든 측정 구간에서 0이었다.

기준 500개 실행은 선택 갱신이 추가로 관측되고 효과 구간 framePasses가 3회뿐이어서, 기준/변경의 효과 전체 호출 합계나 시간을 직접 비교하지 않는다. 표는 관측된 한 처리당 최대 대상 수이며, 동일하게 60프레임을 넣는 500개 단위 테스트로 비대상 호출 0회도 검증했다. Activity FrameMetrics는 지도 렌더링 표본이 부족하므로 FPS 개선율을 주장하지 않는다.

테스트는 store 12개와 효과 UI 4개, 총 16개 통과(실패·오류·skip 0). 실기기에서 성공 효과 도중 발자국·확대, 자연 종료 후 기본 크기·발자국 숨김, 효과 중 다른 전봇대 선택 시 이전 범위 해제와 크기 복원, 빈 바닥 탭 해제를 확인했다. 네 가지 형광색과 인증 체크는 유지된다.

## 3단계: 완성 전봇대 이미지 로딩

지도 진입마다 하던 5개 전봇대의 형광/그림자/체크 합성을 제거했다. 생성기와 원본은 debug에 남기고 제품은 같은 256×640 완성 리소스를 읽는다. 같은 폰 3회 중앙값으로 이미지 준비는 첫 호출 25.79→9.52ms, 재진입 26.59→8.72ms였다. 디코딩/SDK 업로드와 최종 Bitmap 메모리는 남으므로 FPS나 앱 전체 메모리 개선율을 뜻하지 않는다. [생성 절차·대상 테스트·APK 및 리소스 검증](territory-pole-assets.md)에 상세 기록이 있다.
