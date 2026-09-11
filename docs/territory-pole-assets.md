# 전봇대 완성 이미지

지도, 점령 규칙 예시, 범위/성공 효과 미리보기는 `territoryMarkerIcon`으로 같은 완성 WebP를 읽는다. 지도는 화면 생애 동안 다섯 Bitmap 기반 OverlayImage를 공유한다. 준비된 이미지에도 디코딩과 지도 SDK 업로드는 필요하다. Canvas 합성/BlurMaskFilter/중간 Bitmap 생성만 실행 경로에서 제거했다.

## 규격과 원본

- 캔버스 256×640, 지도 크기 48×120, 밑동 anchor (0.5, 624/640). 성공 효과 최대 약 57×143 유지.
- 미점유 기본색 / 내 미인증 파랑 / 내 인증 초록+체크 / 상대 미인증 주황 / 상대 인증 빨강+체크.
- 원본 2장과 기존 합성 코드는 `app/src/debug/res/drawable-nodpi`와 `TerritoryPoleGenerator.kt`에 보관한다. 릴리스에는 포함하지 않는다.
- 현재 결과는 dev `4867f088`의 합성 방식 그대로 SM-S931N(Android API 36)의 software Canvas로 생성했다. 색·그림자·체크를 변경하면 재생성해야 한다. Android/Skia 버전이 바뀌면 생성 결과도 미세하게 달라질 수 있으므로 화면 크기로 다시 확인한다.
- `map_prepared/`는 알파가 있는 256×640 완성 PNG를 무손실 WebP로 변환한다. `map/`의 실루엣 crop/resize를 적용하면 발광 여백과 밑동이 바뀌므로 두 경로를 구분한다. 기존 방/강아지/원본 지도 반입은 기존 q92 경로를 유지한다.

## 재생성

`TerritoryPoleLabActivity`를 포함한 별도 debug APK(`com.daengs.app.cardpreview`)를 먼저 빌드/설치한다. 별도 설치는 Gradle init의 `androidComponents.finalizeDsl`에서 debug `applicationIdSuffix = '.cardpreview'`, `manifestPlaceholders['kakaoScheme'] = 'daengscardpreview'`를 설정하는 방식이다. 실제 앱과 다른 패키지여야 한다. 명령은 저장소 루트에서 실행한다.

```powershell
uv run tools/export_territory_poles.py <임시드롭폴더> --adb <adb.exe경로>
```

이 도구는 해당 debug 패키지의 기존 생성 파일 5개를 비운 뒤 가상 지도 lab을 재시작한다. 생성기가 PNG를 앱 내부에 저장하면 `run-as`로 받아 기존 반입 스크립트를 호출한다. 원본/결과의 크기, 모든 알파, 투명하지 않은 RGB 일치를 검증한다. `--out <임시출력폴더>`로 저장소 밖에 재생성해 비교할 수 있다. 재생성 후 APK를 다시 빌드해야 새 그림이 설치된다. 점령/위치 기록/사진 API는 호출하지 않는다.

## 검증과 비용 (2026-09-11)

실기기 lab의 `measureIcons=true`는 지도 Composable 진입 전 같은 5개 이미지 준비 함수 호출만 측정한다. `legacyIcons=true`면 기존 생성기, `false`면 제품의 완성 이미지 로더다. `exportIcons=true`의 PNG 압축과 파일 저장은 측정 밖이다.

새 프로세스(force-stop 후 실행) 첫 호출과 같은 프로세스의 Activity 종료/재진입을 한 쌍으로 3회 반복했다. OS 파일 캐시를 비운 cold storage 측정은 아니다. 아래는 합성 전/완성 이미지 후 별도 APK를 순서대로 설치한 제한된 관측이며, 지도 표시 완료 시간이나 FPS가 아니다.

| 5장 준비 시간(ms) | 기존 3회 | 변경 3회 | 중앙값 기존 → 변경 |
| --- | --- | --- | --- |
| 프로세스 첫 호출 | 26.04, 25.00, 25.79 | 9.27, 9.63, 9.52 | 25.79 → 9.52 |
| 같은 프로세스 재진입 | 25.06, 26.59, 26.75 | 8.85, 7.70, 8.72 | 26.59 → 8.72 |

- 제품 이미지 리소스: 원본 2장 53,748 bytes → 완성 5장 302,736 bytes(+248,988 bytes). 5장 ARGB 픽셀 메모리는 동일하게 약 3.125 MiB다. 합성에 쓰던 중간 body/halo/alpha mask는 제품에서 만들지 않는다.
- 별도 arm64 debug APK: 97,938,599 → 99,720,514 bytes. debug APK는 생성기 원본 2장도 포함하며 DEX·패키징 차이가 있으므로 릴리스 증가량으로 해석하지 않는다.
- 기준 APK SHA-256: `eb2cfbb6b03e9587f5f6e79ddc3bd9032b4c3c85c62a50f04e0dd7062d3ddb70`.
- 변경 APK SHA-256: `1fbdfd2cf2f472329a9ff50386536eb11603e4a1474a6834ede9380e8e585a87`.
- 두 APK 설치 Success, 각각 기기에서 회수한 APK 해시 일치. 변경 APK 내부 5개 WebP도 저장소와 바이트 일치.
- 실기기 5색/체크/그림자/밑동 및 선택 범위 표시 확인. 밝고 어두운 배경 48×120 비교 그림은 `images/territory-pole-native-sizes.png`.
- 생성 도구로 다시 만든 다섯 WebP와 반입 리소스 해시 일치. 생성 PNG와 WebP의 보이는 RGB/알파도 일치.

대상 테스트: Android 11개(그림 7, 효과 UI 4), Python 6개(완성 반입 2, 원본 지도 반입 4), 실패/오류/skip 0. 전체 테스트는 실행하지 않았다.

```powershell
uv run --with pillow python -m unittest discover -s tools -p test_prepared_map.py
uv run --with pillow python -m unittest discover -s tools -p test_map_sprite.py
./gradlew.bat :app:testDebugUnitTest --tests com.daengs.app.map.layers.territory.TerritoryPoleArtTest --tests com.daengs.app.ui.walk.TerritoryFeedbackUiTest :app:assembleDebug -PslimAbi=arm64-v8a --max-workers=2 --console=plain
```

실기기용 빌드는 위 Gradle 명령에 별도 패키지 설정을 가진 `-I <preview.init.gradle>`를 추가했다. 서버, 게임 정책, 점령 반경, SDK 객체 재사용과 효과 대상 갱신 방식은 이 작업의 변경 대상이 아니다.
