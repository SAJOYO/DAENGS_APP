# 점유 상태별 전봇대 그림

굵은 외곽선과 단순한 명암의 코믹 전봇대를 사용한다. 기본과 점유 그림은 같은 실루엣이며 점유 상태만 밑동에 큰 갈색 얼룩을 표시한다. 살짝 비스듬한 시점, 짧은 금속 가로대, 왼쪽의 해진 흰 포스터를 유지한다. 지도에서 장소 간 여백이 보이도록 본체를 이전의 65%로 줄이고, 짧고 뭉뚝한 사선 고깔 그림자를 붙인다.

| 상태 | 에셋 | 지도 문구 |
| --- | --- | --- |
| NEUTRAL | map_territory_pole_neutral.webp | 선택하면 기존 장소/점유 설명 |
| UNVERIFIED | map_territory_pole_occupied.webp | 미인증 |
| VERIFIED | map_territory_pole_occupied.webp | 인증 |

`WalkMapPresentation`의 기존 점유 상태를 사용한다. 더러움은 점유 유무를 뜻하며 시간이나 방문 횟수에 따라 누적하지 않는다. 미인증과 인증은 같은 에셋을 공유하고 기존 caption과 카드로 구분한다. 기존 원형 배경/점유색 테두리는 제거했다. 접근 링과 잠깐 나타나는 성공 발자국은 기존 반경·시간·색상을 유지한다.

## 에셋과 배치

- 내장 image_gen 도구로 이 작업의 승인된 시안을 편집했다. 외부 에셋 팩이나 외부 저장소 그림이 아니다.
- 승인 시안: `exec-42c46267-8c7d-4eb3-b2d5-6280991bdbf1.png`.
- 기본 원본: `exec-44ccda44-acd7-4bbc-90a4-4eef4060e286.png`.
- 점유 원본: `exec-a63ba3cd-abe0-464f-bd57-38bb7f116700.png` (기본 이미지를 편집해 밑동 얼룩 추가).
- 두 원본 모두 RGBA다. 원본 알파를 보존하며 alpha ≤ 16인 거의 투명한 잡음은 크기/밑동 측정에서 제외한다. 흰 포스터·애자는 지우지 않는다. 기존 불투명 매트 입력도 계속 지원한다.
- 원본은 작업 드롭 폴더의 `map/territory_pole_{neutral,occupied}.png`에 보관했다. 앱은 저장소의 WebP만 사용하므로 생성 도구의 로컬 경로에 의존하지 않는다.
- 반입: `uv run tools/import_room_assets.py <drop>` → `map_sprite.py` → 기존 WebP 품질 92. 기존 방·견종 반입 동작은 유지한다.
- 두 파일 모두 **256×640**, 밑동 접점 **(128,624)**. 바깥 배경을 자른 뒤 밑면 중심을 맞추므로 가로대가 좌우 중심을 바꾸어도 밑동은 움직이지 않는다.
- 네이버 마커 선택 사각형은 **48×120px**, 선택 시 **60×150px**로 유지하고, 내부 본체만 밑동을 중심으로 **65%** 축소한다. 본체 실제 높이는 약 **73px / 91px**다. 성공 확대도 가로·세로를 함께 키운다. anchor는 **(0.5,0.975)**라 확대 시 밑동의 지리 좌표가 유지된다.
- 그림자는 에셋에 굽지 않고 `territoryMarkerIcon`에서 오른쪽 위로 짧게 뻗는 둥근 끝의 고깔 Path로 그린다. 팔 모양이나 원형 받침이 없고 두 상태 모두 같은 색/알파(46/255, 약 18%)다. 작은 그림자 뒤에 본체를 합성한다.
- 합성 비트맵 두 개를 캐시하며 애니메이션 프레임마다 비트맵을 만들지 않는다. 지도와 기존 `TerritoryFeedbackPreview`가 같은 합성 함수를 사용한다.

## 검토 방법

`TerritoryFeedbackPreview`는 지도와 같은 픽셀 크기/접점을 사용한다. `TerritoryPoleArtTest`는 실제 WebP를 읽어 배경 alpha, 흰 포스터 보존, 밑동 명도 차이, 비율을 검증하고 `app/build/reports/territory-pole/native-sizes.png`를 출력한다. 이 그림은 실제 크기 래스터 비교이며 지도 스크린샷은 아니다.

Debug 빌드의 `TerritoryPoleLabActivity`는 실제 `MapHost`와 전봇대 레이어에 가상 장소 세 개를 전달한다. 기본·미인증·인증, 선택 확대, 상태 전환, 링/발자국을 서버 점령이나 GPS 기록 없이 검토한다. 이 진입점은 release에 포함되지 않는다.

```powershell
adb shell am start -n com.daengs.app/.ui.walk.TerritoryPoleLabActivity
```

대상 검증: `uv run --with pillow python -m unittest discover -s tools -p test_map_sprite.py` 4개, `:app:testDebugUnitTest --tests '*TerritoryPoleArtTest' --tests '*TerritoryFeedbackUiTest'` 9개 통과. `:app:assembleDebug -PterritoryServerRead=true` 성공. 지도 키/API 주소는 ignored 로컬 설정이며 저장소에는 넣지 않는다. 에뮬레이터 설치 `Success` 후 APK를 회수해 두 WebP의 SHA-256 일치를 확인한다. 실화면 검증 결과는 PR에 기록하며, 물리 기기와 로그인 뒤 실제 점유 조회는 별도다.

아래는 실제 표시 픽셀 크기다. 왼쪽부터 기본·미인증·인증이고 각 쌍은 기본 크기·선택 크기다. 위는 밝은 배경, 아래는 어두운 배경이다.

![전봇대 실제 픽셀 크기 비교](images/territory-pole-native-sizes.png)
