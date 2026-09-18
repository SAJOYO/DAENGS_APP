# 홈 UX Phase A 구현 계획 — 하단바 · 카드 칸 · 방 가로 늘림

> **에이전트에게:** 이 계획은 `superpowers:subagent-driven-development` 또는
> `superpowers:executing-plans` 로 **한 과제씩** 실행한다. 단계는 `- [ ]` 체크박스다.

**목표:** 홈의 세로 예산을 다시 나눠 방을 키우고, 인벤토리의 이름·개수가 안 보이는
버그를 고치고, 방 가로 늘림 값을 실기기에서 고를 수 있게 만든다.

**접근:** 상수를 바꾸고 순수 함수로 잠근다. 새 개념을 만들지 않는다 — 챗봇 칸과
인벤토리 칸을 떼어내면서 생기는 방 높이 변화만 `animateDpAsState` 로 잇는다.

**기술:** Kotlin · Jetpack Compose · JUnit4 (Robolectric 없이 순수 단위 테스트)

**Spec:** [`docs/home-ux-and-notifications.md`](home-ux-and-notifications.md) — **왜**
그렇게 정했는지는 전부 거기 있다. 이 문서는 **어떻게** 만드는지만 다룬다.

**PR:** [#452](https://github.com/SAJOYO/DAENGS_APP/pull/452) ·
브랜치 `feat/home-ux-and-notifications`

## Phase 나누기

| Phase | 무엇 | 계획 문서 |
| --- | --- | --- |
| **A** | 1 하단 동그라미·시즌 행 · 2 챗봇 칸·인벤토리 · 4 방 가로 늘림 | **이 문서** |
| B | 3 화면 전환 모션 | A 의 실기기 확인 뒤에 쓴다 |
| C | 5 저장소 요약 + 전체보기 | " |
| D | 6 알림 다섯 | " |
| E | 7 날짜·이름표 자리 고르기 | " |

**A 를 먼저 하는 이유:** 1·2 가 세로 예산을 정하고 4 가 그 위에서 가로를 정한다.
셋 다 실기기에서 사용자가 숫자를 확정해야 하고, **그 확정값이 B~E 의 전제**다.
A 없이 B 를 하면 모션을 붙인 화면을 다시 재배치하게 된다.

## Global Constraints

이 Phase 의 모든 과제에 걸린다.

- **색은 앱 테마에서만 가져온다.** `Color(0x…)` 를 화면 파일에서 만들지 않는다
  (`docs/design-locks.md` 0절).
- **`BarHeight = 56.dp` 를 줄이지 않는다.** 50dp 로 줄이면 탭 라벨 아래가 깎인다
  (2026-09-18 실기기 확인, `DaengsBottomBar.kt:67`).
- **가운데 버튼은 48dp 이상.** 손가락이 닿는 최소치다.
- **여기 나오는 모든 dp 는 합성 안 dp 다.** `DaengsTheme` 이 `LocalDensity` 를
  `Density(base.density × uiScale(짧은 변))` 로 갈아끼워 **가로를 항상 411dp 로
  맞춘다** (`ui/theme/Theme.kt:115` · `ui/theme/UiScale.kt:15`). 실기기에서 재려면
  `wm density` 가 아니라 이 자를 써야 한다 — Pixel 3 XL 은 `1dp = 3.504px` 다.
- **커밋 메시지는 한글 서술형이고 접두사가 없다.** 제목에 무엇이 어떻게 잘못돼
  있었는지를 쓰고(`~던 것`), 본문에 왜 그렇게 고쳤는지와 무엇을 재봤는지를 쓴다.
  끝에 `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`.
- **빌드·테스트 명령:** `./gradlew :app:assembleDebug :app:testDebugUnitTest`
  (Windows Git Bash 에서는 `JAVA_HOME` 을 직접 줘야 한다).
- **`@Preview` 를 붙인다.** Composable 을 새로 만들거나 고치면 예외 없다.

## 파일 구조

| 파일 | 책임 | 과제 |
| --- | --- | --- |
| `ui/home/DaengsBottomBar.kt` | 하단바·레일 규격 상수와 배치 | 1 |
| `ui/home/HomeGameCard.kt` | 시즌 한 줄. **높이 상수를 새로 내보낸다** | 1 |
| `ui/home/HomeScreen.kt` | 홈 배치. 카드 칸 높이·`Spacer`·방 상자 | 1 · 2 · 3 |
| `ui/home/ChatbotCard.kt` | 챗봇 카드 내부 치수 | 2 |
| `ui/home/InventoryPanel.kt` | 인벤토리 패널·슬롯. **치수를 `InventoryMetrics` 로 내보낸다** | 2 |
| `miniroom/IsoMath.kt` | 방 기하. 낡은 주석 수정 + `of()` 에 늘림 인자 | 3 |
| `miniroom/MiniRoomCanvas.kt` | 늘림 값을 `of()` 호출 아홉 곳에 나른다 | 3 |
| `src/debug/.../DeveloperPanel.kt` | 늘림 슬라이더 (디버그 전용) | 3 |
| `src/release/.../DeveloperPanel.kt` | 같은 시그니처의 빈 스텁 | 3 |

**새로 만드는 테스트**

| 파일 | 무엇을 잡나 |
| --- | --- |
| `test/…/ui/home/HomeBottomBarLockTest.kt` | 하단바·시즌 행 규격 (사용자가 정한 값) |
| `test/…/ui/home/InventoryMetricsTest.kt` | 슬롯이 필요한 높이를 받는가 (지금 깨진 그 자리) |
| `test/…/miniroom/RoomStretchTest.kt` | 가로 늘림이 상자 폭을 넘지 않는가 |

---

## Task 1: 하단 동그라미와 시즌 행

동그라미를 52dp 로 줄이고 바 위로 6dp 만 나오게 한다. 시즌 행 띠를 58dp(실측)에서
28dp 로 고정하고 그 아래 `Spacer` 를 없앤다. 결과로 내용 영역이 16dp 넓어지고 카드
높이가 30dp 줄어, **방 상자가 275.6 → 335.3dp** 가 된다.

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/home/DaengsBottomBar.kt:70-72`
- Modify: `app/src/main/java/com/daengs/app/ui/home/HomeGameCard.kt:57-82`
- Modify: `app/src/main/java/com/daengs/app/ui/home/HomeScreen.kt:650`
- Test: `app/src/test/java/com/daengs/app/ui/home/HomeBottomBarLockTest.kt`

**Interfaces:**
- Produces: `internal val BarHeight/FabSize/FabLift` in `ui.home` (지금은 `private`)
- Produces: `internal val HomeGameRowHeight: Dp` in `ui.home`
- Task 4 가 이 상수들을 `docs/design-locks.md` 에 잠근다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`app/src/test/java/com/daengs/app/ui/home/HomeBottomBarLockTest.kt`:

```kotlin
package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **사용자가 실기기에서 정한 하단 규격.**
 *
 * ⛔ 이 테스트가 깨지면 테스트를 고치지 말고 변경을 되돌린다 — `docs/design-locks.md`.
 *
 * 지름 58dp 중 22dp(38%)가 흰 바 위로 나와 있어서, 바의 일부가 아니라 바 위에 얹힌
 * 별개의 물체로 읽혔다. 그리고 시즌 행과 흰 바 사이가 47.1dp 비어 있었다 —
 * 내용 영역 안 25.1dp + 투명한 띠 22dp.
 */
class HomeBottomBarLockTest {

    @Test
    fun `가운데 버튼 지름은 52dp 다`() {
        assertEquals(52.dp, FabSize)
    }

    /** 지름의 12%. 38% 였을 때 "바 위에 얹힌 별개의 물체" 로 읽혔다. */
    @Test
    fun `가운데 버튼이 흰 바 위로 나오는 양은 6dp 다`() {
        assertEquals(6.dp, FabLift)
    }

    /** ⚠️ 50dp 로 줄이면 탭 라벨 아래가 깎인다 (2026-09-18 실기기). */
    @Test
    fun `바 높이 56dp 는 지킨다`() {
        assertEquals(56.dp, BarHeight)
    }

    @Test
    fun `가운데 버튼은 터치 최소치를 넘는다`() {
        assertTrue("$FabSize 는 48dp 미만이다", FabSize >= 48.dp)
    }

    /** 글자가 17.4dp 인데 M3 최소높이 때문에 58dp 를 먹고 있었다. */
    @Test
    fun `시즌 행 높이는 28dp 다`() {
        assertEquals(28.dp, HomeGameRowHeight)
    }

    /** 글자 17.4dp 가 들어가고 위아래로 숨 쉴 만큼은 남아야 한다. */
    @Test
    fun `시즌 행에 글자 한 줄이 들어간다`() {
        assertTrue("$HomeGameRowHeight 에 17.4dp 글자가 안 들어간다", HomeGameRowHeight >= 24.dp)
    }
}
```

- [ ] **Step 2: 컴파일이 깨지는 것을 확인한다**

```bash
./gradlew :app:testDebugUnitTest --tests '*HomeBottomBarLockTest*'
```

기대: **컴파일 실패.** `FabSize`·`FabLift`·`BarHeight` 는 `private` 이고
`HomeGameRowHeight` 는 아직 없다.

- [ ] **Step 3: 상수를 내보내고 값을 바꾼다**

`DaengsBottomBar.kt:67-72` 를 이렇게 바꾼다.

```kotlin
// ⚠️ **50dp 로 줄이면 탭 라벨 아래가 깎인다** (2026-09-18 실기기 확인). 아이콘 24dp 에
// 라벨 한 줄과 위아래 여백을 더한 값이라 더 줄일 자리가 없다.
internal val BarHeight = 56.dp

/**
 * 가운데 버튼 지름. **48dp 아래로 내리지 않는다** — 손가락이 닿는 최소치다.
 *
 * 58dp 였다. 사용자가 실기기에서 "조금 줄이고" 로 정했다.
 */
internal val FabSize = 52.dp

/**
 * 가운데 버튼이 흰 바 **위로** 나오는 양.
 *
 * 이 숫자 하나가 둘을 같이 움직인다 — 나온 양이 지름의 38%→12% 가 되고, 흰 바 위쪽의
 * **투명한 띠**가 그만큼 얇아져 내용 영역이 넓어진다. 그 몫은 방이 받는다
 * (방이 `weight(1f)` 라 남는 것을 다 가져간다).
 *
 * 22dp 였다. 시즌 행과 흰 바 사이가 47.1dp 비어 보이던 원인의 절반이 이것이었다.
 */
internal val FabLift = 6.dp
```

`HomeGameCard.kt` 에 높이 상수를 더하고 `TextButton` 에 먹인다.

```kotlin
/**
 * 시즌 한 줄의 높이.
 *
 * **재서 정했다.** 글자는 17.4dp 인데 `TextButton` 이 `ButtonDefaults.MinHeight` 로
 * 58dp 를 먹고 있었다 (Pixel 3 XL 실측). `LocalMinimumInteractiveComponentSize` 를
 * 풀어 둔 것만으로는 안 줄었다 — 그건 48dp 터치 타깃을 푸는 것이고 최소 높이는 별개다.
 * 그래서 높이를 **직접 준다.**
 */
internal val HomeGameRowHeight = 28.dp
```

```kotlin
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    TextButton(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeGameRowHeight)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
```

`import androidx.compose.foundation.layout.height` 를 더한다 (이 파일은
`layout.*` 와일드카드 import 를 쓰므로 이미 들어와 있다 — 확인만 한다).

`HomeScreen.kt:650` 의 `Spacer` 를 없앤다.

```kotlin
            gameContent?.invoke()
            // 시즌 행 아래 여백은 0 이다. 하단바 상자의 투명한 띠(FabLift 6dp)가
            // 이미 그 몫을 한다 — 여기에 더 두면 시즌 행이 바에서 떠 보인다.
```

- [ ] **Step 4: 테스트가 통과하는 것을 확인한다**

```bash
./gradlew :app:testDebugUnitTest --tests '*HomeBottomBarLockTest*'
```

기대: **6개 통과.**

- [ ] **Step 5: 전체 단위 테스트가 안 깨진 것을 확인한다**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

기대: 통과. ⚠️ `WideLayoutTest` 가 `homeScrolls` 경계를 카드 덩어리 합으로 잡고 있다
(`ui/home/WideLayout.kt`). 시즌 행이 30dp 줄었으니 **그 경계가 움직인다** — 깨지면
`WideLayout.kt` 의 카드 덩어리 상수를 같이 고치고, `WideLayoutTest` 의 기대값도
**계산 근거를 주석에 적고** 고친다. (이건 테스트를 약화하는 것이 아니라 전제가
바뀐 것이다 — 사용자가 정한 잠금이 아니다.)

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/daengs/app/ui/home/DaengsBottomBar.kt \
        app/src/main/java/com/daengs/app/ui/home/HomeGameCard.kt \
        app/src/main/java/com/daengs/app/ui/home/HomeScreen.kt \
        app/src/test/java/com/daengs/app/ui/home/HomeBottomBarLockTest.kt
git commit -F <메시지 파일>
```

커밋 제목: `가운데 발바닥 버튼이 바 위로 38% 나와 있고 시즌 행이 바에서 47dp 떠 있던 것`

---

## Task 2: 챗봇 칸을 줄이고 인벤토리를 떼어낸다

챗봇 카드를 88dp 로 줄인다. 인벤토리는 제 높이(114dp)를 쓰게 떼어내 **이름·개수가
안 보이는 버그를 고친다.** 두 칸의 높이 차이는 `animateDpAsState` 로 잇는다.

**지금 깨져 있는 것:** 슬롯이 88dp 를 요구하는데 55.6dp 만 받아서 이름과 개수가
화면 계층에 **아예 없다** (`uiautomator dump` 로 확인).

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/home/InventoryPanel.kt:77-117` · `:120-152`
- Modify: `app/src/main/java/com/daengs/app/ui/home/HomeScreen.kt:89` · `:620-641`
- Modify: `app/src/main/java/com/daengs/app/ui/home/ChatbotCard.kt:70-120`
- Test: `app/src/test/java/com/daengs/app/ui/home/InventoryMetricsTest.kt`

**Interfaces:**
- Consumes: 없음 (Task 1 과 독립이지만 같은 세로 예산을 쓰므로 순서대로 한다)
- Produces: `internal object InventoryMetrics` in `ui.home` — `Panel`·`Slot`·`Thumb` 등
- Produces: `internal val CardSlotHeight = 88.dp` (지금 104dp)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`app/src/test/java/com/daengs/app/ui/home/InventoryMetricsTest.kt`:

```kotlin
package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 인벤토리 슬롯이 **자기가 필요한 높이를 받는가.**
 *
 * 여기가 깨져 있었다 — 슬롯이 88dp 를 요구하는데 칸이 104dp 밖에 없어서, 패딩과
 * 탭 줄을 뺀 55.6dp 만 받았다. 썸네일(53dp)까지만 들어가고 **이름과 개수가 배치조차
 * 안 됐다** (Pixel 3 XL 에서 `uiautomator dump` 에 그 글자 노드가 없었다).
 *
 * 챗봇 카드와 같은 칸을 쓰던 것을 떼어내면서 함께 고친다. 눈으로는 "썸네일만 있는
 * 깔끔한 줄" 로 보여서 아무도 버그라고 생각하지 않았다 — 그래서 숫자로 잡는다.
 */
class InventoryMetricsTest {

    @Test
    fun `패널 높이는 슬롯과 탭 줄을 다 담는다`() {
        val need = InventoryMetrics.PanelPadding * 2 +
            InventoryMetrics.TabRow +
            InventoryMetrics.Gap +
            InventoryMetrics.Slot
        assertTrue(
            "패널 ${InventoryMetrics.Panel} 에 필요한 것 $need 이 안 들어간다",
            InventoryMetrics.Panel >= need,
        )
    }

    @Test
    fun `슬롯 높이는 썸네일과 이름 한 줄을 담는다`() {
        val need = InventoryMetrics.SlotPadding * 2 +
            InventoryMetrics.Thumb +
            InventoryMetrics.Label
        assertEquals(need, InventoryMetrics.Slot)
    }

    /** 픽셀 아트라 썸네일이 작아지면 러그와 방석을 구분할 수 없다. */
    @Test
    fun `썸네일은 40dp 보다 작아지지 않는다`() {
        assertTrue("${InventoryMetrics.Thumb} 는 너무 작다", InventoryMetrics.Thumb >= 40.dp)
    }

    /** 챗봇 칸(88dp)보다 크다 — 그래서 방이 움직이고, 그 움직임을 애니메이션으로 잇는다. */
    @Test
    fun `인벤토리가 챗봇 칸보다 크다`() {
        assertTrue(InventoryMetrics.Panel > CardSlotHeight)
    }

    /** 방이 움직이는 양. 48dp 는 과했고 26dp 로 잡았다 (사용자 결정). */
    @Test
    fun `방이 움직이는 양은 30dp 를 넘지 않는다`() {
        assertTrue(
            "방이 ${InventoryMetrics.Panel - CardSlotHeight} 움직인다",
            InventoryMetrics.Panel - CardSlotHeight <= 30.dp,
        )
    }

    @Test
    fun `챗봇 칸은 88dp 다`() {
        assertEquals(88.dp, CardSlotHeight)
    }
}
```

- [ ] **Step 2: 컴파일이 깨지는 것을 확인한다**

```bash
./gradlew :app:testDebugUnitTest --tests '*InventoryMetricsTest*'
```

기대: **컴파일 실패** — `InventoryMetrics` 가 없다.

- [ ] **Step 3: `InventoryMetrics` 를 만들고 패널을 그 값으로 그린다**

`InventoryPanel.kt` 맨 위(`InventoryPanel` 정의 앞)에 더한다.

```kotlin
/**
 * 인벤토리 패널의 치수 — **한 곳에 모아 테스트로 잡는다.**
 *
 * 흩어 두었더니 칸 높이(104dp)와 슬롯이 필요한 높이(88dp)가 어긋난 것을 아무도
 * 못 봤다. 눈으로는 "썸네일만 있는 깔끔한 줄" 이라 버그로 보이지 않았다.
 *
 * 이름과 개수를 **한 줄로 합쳤다** (`러그 ×2`). 두 줄이면 패널이 136dp 가 되어
 * `+` 를 누를 때 방이 48dp 움직인다 — 사용자가 26dp 로 정했다.
 * 원래 주석이 개수를 아래로 내린 이유는 "썸네일 위에 0 을 겹쳐 쓰면 중복" 이었고,
 * 한 줄로 합치는 것은 그 이야기가 아니다.
 */
internal object InventoryMetrics {
    val PanelPadding = 8.dp
    val TabRow = 24.dp
    val Gap = 5.dp
    val SlotPadding = 5.dp

    /** 픽셀 아트라 여기서 더 줄이면 러그와 방석을 구분할 수 없다. */
    val Thumb = 46.dp

    /** `러그 ×2` 한 줄. */
    val Label = 13.dp

    val Slot: Dp = SlotPadding * 2 + Thumb + Label
    val Panel: Dp = PanelPadding * 2 + TabRow + Gap + Slot
}
```

`import androidx.compose.ui.unit.Dp` 를 더한다.

`InventoryPanel` 의 `Column` 패딩을 `InventoryMetrics.PanelPadding` 으로,
`Spacer` 를 `InventoryMetrics.Gap` 으로 바꾼다.

`InventorySlot` 을 한 줄 라벨로 바꾼다 (`:128-152`).

```kotlin
    Column(
        Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (enabled) PinkFaint else PinkFaint.copy(alpha = 0.4f))
            .padding(vertical = InventoryMetrics.SlotPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemThumb(art, Modifier.size(InventoryMetrics.Thumb).alpha(if (enabled) 1f else 0.3f))
        // **이름과 개수를 한 줄로 합친다.** 두 줄이면 패널이 136dp 가 되어 방이
        // 48dp 움직인다. 개수를 썸네일 위에 겹치지 않는 것은 그대로다.
        Text(
            "${ItemLabels[id] ?: id} ×$count",
            color = if (enabled) TextDark else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
```

- [ ] **Step 4: 칸 높이를 88dp 로 내리고 두 칸을 떼어낸다**

`HomeScreen.kt:81-89` 의 주석과 값을 바꾼다.

```kotlin
/**
 * 챗봇 카드가 쓰는 칸 높이.
 *
 * **예전에는 인벤토리 패널과 같은 칸이었다.** 둘의 높이가 다르면 방이 `weight(1f)` 로
 * 남는 높이를 가져가기 때문에 인벤토리를 열고 닫을 때마다 방이 그 차이만큼 튀었다.
 * 그래서 묶어 뒀는데, **그 대가로 인벤토리 슬롯의 이름과 개수가 배치되지 않았다**
 * (104dp 에 88dp 짜리 슬롯). 지금은 떼어내고 높이 변화를 애니메이션으로 잇는다 —
 * 튀는 게 아니라 미끄러지면 "편집 도구가 올라온다" 로 읽힌다.
 *
 * 104dp 였다. 안에 든 것은 `패딩 7×2 + 제목줄 25 + Spacer 5 + 입력줄 44` 다.
 */
internal val CardSlotHeight = 88.dp
```

`HomeScreen.kt:620-641` 의 `cards` 를 바꾼다.

```kotlin
        val cards: @Composable ColumnScope.() -> Unit = {
            // **칸 높이가 두 값 사이를 오간다.** 방이 `weight(1f)` 로 남는 높이를
            // 가져가므로, 이 한 값이 곧 방 크기다. 튀지 않게 잇는다.
            val slotHeight by animateDpAsState(
                targetValue = if (inventoryOpen) InventoryMetrics.Panel else CardSlotHeight,
                animationSpec = tween(durationMillis = 220),
                label = "카드 칸 높이",
            )
            val slot = Modifier.padding(horizontal = 14.dp).height(slotHeight)
            if (inventoryOpen) {
                // **자라는 동안 내용을 같이 띄운다.** 칸이 88→114 로 커지는 220ms 사이에
                // 알파 없이 그리면 그 동안만 이름·개수가 다시 잘려 보인다 — 방금 고친
                // 바로 그 증상이라 혼란스럽다.
                val appear by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220),
                    label = "인벤토리 나타남",
                )
                InventoryPanel(
                    catalog = catalog,
                    available = { roomState.availableCount(it) },
                    onPick = { roomState.placeFromInventory(it, catalog) },
                    currentTheme = roomTheme,
                    onPickTheme = {
                        themeId = it.id
                        store.saveThemeId(it.id)
                    },
                    modifier = slot.graphicsLayer { alpha = appear },
                )
            } else {
                ChatbotCard(
                    onOpenChat = { onOpenChat?.invoke() },
                    onVoice = { onOpenChatByVoice?.invoke() },
                    modifier = slot.tourSpot(tourSpots, TourStop.Chat),
                    avatar = profileBreed,
                )
            }
            Spacer(Modifier.height(6.dp))
            WalkSummaryCard(
                Modifier.padding(horizontal = 14.dp),
                todayWalks,
                words.daily,
                onOpenWalkHistory,
            )
            gameContent?.invoke()
        }
```

import 를 더한다.

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
```

- [ ] **Step 5: 챗봇 카드 안쪽을 88dp 에 맞춘다**

`ChatbotCard.kt:70-120`. 패딩 `vertical = 9.dp` → `7.dp`, `Spacer(8.dp)` → `5.dp`,
입력줄 `height(46.dp)` → `44.dp`(모서리 `23.dp` → `22.dp`), 아바타·마이크·전송
`size(40.dp)` → `38.dp`.

```kotlin
        // ⚠️ **칸 높이는 [HomeScreen.CardSlotHeight] 로 고정이다** — 88dp 다.
        // 안에 든 것: 패딩 7×2 + 제목줄 25 + Spacer 5 + 입력줄 44.
        // 인벤토리 패널은 **더 이상 이 칸을 같이 쓰지 않는다** (제 높이 114dp).
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
```

- [ ] **Step 6: 테스트가 통과하는 것을 확인한다**

```bash
./gradlew :app:testDebugUnitTest --tests '*InventoryMetricsTest*'
```

기대: **7개 통과.** `Slot = 5×2 + 46 + 13 = 69dp`, `Panel = 8×2 + 24 + 5 + 69 = 114dp`,
`Panel − CardSlotHeight = 26dp`.

- [ ] **Step 7: 전체 테스트와 `@Preview` 확인**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

`ChatbotCardPreview` 와 `InventoryPanelPreview` 에 `Modifier.height(...)` 를 주어
실제 칸 높이로 보이게 한다 — 지금 둘 다 자기 크기대로 그려서 칸에 들어갔을 때와 다르다.

```kotlin
@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatbotCardPreview() {
    DaengsTheme {
        ChatbotCard(
            onOpenChat = {},
            onVoice = {},
            // **칸 높이를 준다.** 안 주면 카드가 자기 크기대로 그려져서 실제 홈과
            // 다르게 보인다 — 여백을 조정할 때 이 미리보기를 보고 판단하게 된다.
            modifier = Modifier.padding(14.dp).height(CardSlotHeight),
            avatar = HomeDemoData.DOG_BREED,
        )
    }
}
```

- [ ] **Step 8: 커밋**

커밋 제목: `인벤토리 슬롯의 이름과 개수가 칸에 안 들어가 배치조차 안 되던 것`

---

## Task 3: 방 가로 늘림을 실기기에서 고를 수 있게 한다

`H_STRETCH` 의 **낡은 주석을 고치고**, 값을 실기기에서 돌려 볼 수 있게 개발자 패널에
슬라이더를 붙인다. 기본값은 1.18 그대로 둔다 — 사용자가 고르기 전까지 화면이 안 변한다.

**왜 주석부터인가:** `H_STRETCH` 주석은 *"강아지와 소품은 안 늘어난다… 여러 칸을
차지하는 소품(러그 5x5)은 자기 칸을 다 덮지 못한다"* 고 말한다. 그런데 나중에 추가된
`RoomGeometry.scaleX` 가 **소품에는 가로 배율을 주고 강아지에는 안 준다** —
그 문제는 이미 해결됐다. 두 주석이 서로 모순하고, **낡은 쪽을 읽으면 "늘리면 러그가
깨진다" 는 틀린 근거로 판단하게 된다.**

**Files:**
- Modify: `app/src/main/java/com/daengs/app/miniroom/IsoMath.kt:79-93` · `:340-354`
- Modify: `app/src/main/java/com/daengs/app/miniroom/MiniRoomCanvas.kt` (`of(` 호출 6곳)
- Modify: `app/src/main/java/com/daengs/app/ui/home/HomeScreen.kt` (dev 값 전달)
- Modify: `app/src/debug/java/com/daengs/app/ui/home/DeveloperPanel.kt`
- Modify: `app/src/release/java/com/daengs/app/ui/home/DeveloperPanel.kt` (시그니처 일치)
- Test: `app/src/test/java/com/daengs/app/miniroom/RoomStretchTest.kt`

**Interfaces:**
- Produces: `RoomGeometry.of(widthPx: Float, heightPx: Float, hStretch: Float = RoomSpec.H_STRETCH)`
- Produces: `DeveloperPanel(..., hStretch: Float, onPickHStretch: (Float) -> Unit)`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`app/src/test/java/com/daengs/app/miniroom/RoomStretchTest.kt`:

```kotlin
package com.daengs.app.miniroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 방을 **가로로만** 늘리는 값의 성질.
 *
 * 이 값은 "각 폰에서 방을 최대한 크게" 의 손잡이다. 길쭉한 폰은 이미 상자 폭에
 * 닿아 있고(Pixel 7 은 1.14 에서 한계), 정사각에 가까운 폰만 모자란다
 * (Pixel 3 XL 은 한계까지 1.53 이 필요하다).
 *
 * ⚠️ `H_STRETCH` 의 옛 주석은 "여러 칸짜리 소품이 칸을 못 덮는다" 고 말하지만
 * [RoomGeometry.scaleX] 가 그것을 이미 고쳤다. 소품은 같이 늘어나고 강아지는 안
 * 늘어난다 — 여기서 그 계약을 잡는다.
 */
class RoomStretchTest {

    /** 합성 안에서 폭은 늘 411dp 다 (`DaengsTheme` 이 자를 맞춘다). 3.504px/dp. */
    private val w = 411.4f * 3.504f
    private val h = 335.3f * 3.504f  // 1·2 번을 반영한 방 상자

    @Test
    fun `기본값은 1_18 이다`() {
        assertEquals(1.18f, RoomSpec.H_STRETCH, 1e-6f)
    }

    @Test
    fun `늘리면 방이 넓어진다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.40f)
        assertTrue("1.40 이 1.18 보다 안 넓다", b.stage.width > a.stage.width)
    }

    @Test
    fun `늘려도 세로는 그대로다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.40f)
        assertEquals(a.stage.height, b.stage.height, 0.01f)
    }

    /** 넘치게 잡아도 상자 폭에서 잘린다. 화면 밖으로 안 나간다. */
    @Test
    fun `넘치게 잡아도 상자 폭을 넘지 않는다`() {
        val g = RoomGeometry.of(w, h, hStretch = 9f)
        assertTrue("방이 ${g.stage.width} 로 상자 $w 를 넘었다", g.stage.width <= w + 0.01f)
    }

    /** 늘림은 가로에만 걸린다 — 강아지가 쓰는 [RoomGeometry.scale] 은 그대로다. */
    @Test
    fun `늘림은 균일 배율을 바꾸지 않는다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.50f)
        assertEquals(a.scale, b.scale, 1e-6f)
        assertTrue("소품 배율이 안 늘어났다", b.scaleX > a.scaleX)
    }

    /** 늘리지 않으면 가로·세로 배율이 같다. */
    @Test
    fun `늘림이 1이면 원본 비율이다`() {
        val g = RoomGeometry.of(w, h, hStretch = 1f)
        assertEquals(g.scale, g.scaleX, 1e-6f)
    }
}
```

- [ ] **Step 2: 컴파일이 깨지는 것을 확인한다**

```bash
./gradlew :app:testDebugUnitTest --tests '*RoomStretchTest*'
```

기대: **컴파일 실패** — `of()` 에 `hStretch` 인자가 없다.

- [ ] **Step 3: `of()` 에 인자를 더하고 낡은 주석을 고친다**

`IsoMath.kt:79-93` 의 주석을 바꾼다.

```kotlin
    /**
     * 방 그림을 **가로로만** 더 늘리는 배율. 1 이면 원본 비율.
     *
     * 방 PNG 가 세로로 길어서(1122x1402) 가로가 넓은 상자에서는 세로가 먼저 걸리고,
     * 좌우에 큰 여백이 남는다 — 1080 폭 화면에서 방이 691px 이라 양쪽에 195px 씩
     * 놀았다. 그 여백을 방에 준다.
     *
     * **소품은 같이 늘어나고 강아지는 안 늘어난다** ([RoomGeometry.scaleX] 참고).
     * 방과 격자가 가로로 늘어나면 여러 칸짜리 소품(러그 5x5)이 자기 칸을 못 덮으므로
     * 소품은 같이 늘리고, 강아지까지 늘리면 개가 뚱뚱해지므로 강아지는 놔둔다.
     *
     * ⚠️ **예전 주석은 "강아지와 소품은 안 늘어난다" 였고, 러그가 칸을 못 덮는 것을
     * 현재의 대가로 적어 두었다.** [RoomGeometry.scaleX] 가 나중에 추가되면서 그 문제는
     * 해결됐는데 이 주석만 남아 있었다. 그걸 읽고 "늘리면 러그가 깨진다" 고 판단하면
     * 틀린 근거로 결정하게 된다 (2026-09-18).
     *
     * 지금 늘림의 실제 대가는 **모양**이다 — 아이소메트릭이 옆으로 퍼지고, 픽셀 아트의
     * 가로 도트가 세로보다 굵어지고, 안 늘어나는 강아지가 상대적으로 작아 보인다.
     * 견딜 만한 값은 실기기에서 눈으로 고른다 (개발자 패널에 슬라이더가 있다).
     *
     * 넘치게 잡아도 상자 폭에서 잘린다([RoomGeometry.of] 가 min 을 건다).
     */
    const val H_STRETCH = 1.18f
```

`IsoMath.kt:340` 의 `of` 에 인자를 더한다.

```kotlin
        /**
         * @param hStretch 가로만 더 늘리는 배율. 기본은 [RoomSpec.H_STRETCH] 이고,
         *   개발자 패널이 실기기에서 값을 고를 때만 다른 값이 들어온다.
         */
        fun of(
            widthPx: Float,
            heightPx: Float,
            hStretch: Float = RoomSpec.H_STRETCH,
        ): RoomGeometry {
            val contain = min(widthPx / RoomSpec.ROOM_PNG_W, heightPx / RoomSpec.ROOM_PNG_H)
            val s = contain * RoomSpec.INSET
            // 가로만 더 준다. 상자 폭을 넘지 않게 잘라서, 값을 크게 잡아도 잘리지 않는다.
            val sx = min(s * hStretch, widthPx * RoomSpec.INSET / RoomSpec.ROOM_PNG_W)
```

반환문에 `sx` 가 이미 세 번째 인자로 들어간다 — `RoomGeometry(Rect(...), s, sx)`. 그대로다.

`of(widthPx)` 오버로드도 인자를 받게 한다.

```kotlin
        /** 가로만 아는 경우 — 그림 비율대로 세로를 잡는다. */
        fun of(widthPx: Float, hStretch: Float = RoomSpec.H_STRETCH): RoomGeometry =
            of(widthPx, widthPx / RoomSpec.ASPECT, hStretch)
```

⚠️ **`of(900f)` 를 쓰는 기존 테스트(`DoorHitTest.kt:22`)가 그대로 컴파일되어야 한다** —
기본 인자라 그렇다. 확인할 것.

- [ ] **Step 4: 늘림 값을 `MiniRoomCanvas` 로 나른다**

`MiniRoomCanvas` 의 `@Composable` 진입점에 인자를 더하고, 그 안의 `RoomGeometry.of(`
호출 여섯 곳(`:206`·`:231`·`:262`·`:275`·`:357`·`:379`)에 `hStretch` 를 넘긴다.

```kotlin
    /**
     * 방 가로 늘림. **개발자 패널이 실기기에서 값을 고를 때만** 기본값과 다르다.
     * 릴리스에서는 언제나 [RoomSpec.H_STRETCH] 다 — 패널이 빈 스텁이기 때문이다.
     */
    hStretch: Float = RoomSpec.H_STRETCH,
```

`HomeScreen.kt` 에서 dev 상태를 들고 내려 준다. `developer` 토글 옆에 이미
`breedOverride` 같은 세션 한정 상태가 있으므로 같은 자리에 둔다.

```kotlin
    // 저장하지 않는 세션 한정 값이다 — 개발자 패널에서만 바뀐다.
    var hStretch by remember { mutableStateOf(RoomSpec.H_STRETCH) }
```

- [ ] **Step 5: 개발자 패널에 슬라이더를 붙인다**

`app/src/debug/java/com/daengs/app/ui/home/DeveloperPanel.kt` 의 `DeveloperPanel` 에
인자 둘을 더하고 슬라이더 한 줄을 그린다.

```kotlin
    /**
     * 방 가로 늘림. 1.0(원본 비율) ~ 1.6.
     *
     * **한계는 기기마다 다르다** — `RoomGeometry.of` 가 상자 폭에서 자르므로, 값을
     * 올려도 어느 지점부터 방이 더 안 넓어진다. Pixel 3 XL(방 상자 335dp)은 1.53,
     * Pixel 7(452dp)은 1.14 에서 한계다. 그래서 상한을 넉넉히 1.6 으로 둔다.
     */
    hStretch: Float,
    onPickHStretch: (Float) -> Unit,
```

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가로", color = PanelText, fontSize = 9.sp)
            Slider(
                value = hStretch,
                onValueChange = onPickHStretch,
                valueRange = 1f..1.6f,
                modifier = Modifier.width(96.dp).height(20.dp),
            )
            Text(String.format("%.2f", hStretch), color = PanelPick, fontSize = 9.sp)
        }
```

`app/src/release/java/com/daengs/app/ui/home/DeveloperPanel.kt` 의 스텁에 **같은 두
인자**를 더한다. 본문은 그대로 빈 함수다 — 릴리스 APK 에는 코드가 안 들어간다.

- [ ] **Step 6: 테스트와 빌드를 확인한다**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --tests '*RoomStretchTest*'
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

기대: `RoomStretchTest` 6개 통과, 전체 통과, **릴리스도 컴파일된다.**

```bash
./gradlew :app:assembleRelease
```

⚠️ 릴리스 스텁의 시그니처가 어긋나면 **디버그는 통과하고 릴리스만 깨진다.** 반드시 돌린다.

- [ ] **Step 7: 커밋**

커밋 제목: `가로 늘림 주석이 "러그가 칸을 못 덮는다" 고 말하는데 이미 고쳐져 있던 것`

---

## Task 4: 실기기에서 숫자를 확정하고 잠근다

**이 과제는 사용자가 한다.** 에이전트는 설치까지 하고 결과를 받아 굳힌다.

- [ ] **Step 1: 설치하고 `Success` 를 눈으로 본다**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

⚠️ **출력을 버리지 않는다.** 이 폰은 `adb` 인증이 수시로 풀려 조용히 실패한다.
`Success` 가 뜨는지 직접 본다 (`CLAUDE.md`).

- [ ] **Step 2: 사용자가 세 가지를 본다**

1. **1번** — 동그라미 크기와 시즌 행이 바에 붙은 모양. 좁으면 `HomeScreen` 의
   시즌 행 아래에 `Spacer(4~6.dp)` 를 되돌린다 (숫자 하나)
2. **2번** — `+` 를 눌러 인벤토리에 **이름·개수가 보이는지**, 방이 26dp 물러나는 것이
   어색하지 않은지
3. **4번** — `DEV` 를 켜고 가로 슬라이더를 1.18 → 1.53 으로 돌려 보며 값을 고른다

- [ ] **Step 3: 고른 값을 상수로 굳힌다**

`RoomSpec.H_STRETCH` 를 그 값으로 바꾸고, **왜 그 값인지**(어디서 퍼져 보이기
시작했는지)를 주석에 적는다. `RoomStretchTest` 의 `기본값은 1_18 이다` 를 새 값으로
고치고 테스트 이름도 같이 바꾼다.

- [ ] **Step 4: `docs/design-locks.md` 에 절을 더한다**

6절로 더한다 — 하단바 규격(`FabSize` 52 · `FabLift` 6 · `BarHeight` 56 · 시즌 행 28),
카드 칸(챗봇 88 · 인벤토리 114), 방 가로 늘림(고른 값). **사용자가 실기기에서 정한
값**이라고 적고 잠금 테스트 이름을 함께 적는다.

- [ ] **Step 5: `STATUS.md` 를 갱신한다**

「알려진 문제」에서 없어진 것을 지우고(인벤토리 이름·개수), 「다음 결정」 5번(방 크기)에
**"가로 늘림으로 어디까지 갔는지"** 를 적는다. 그 항목이 파킹해 둔 두 길(가로형 방 에셋 /
홈 스크롤)은 그대로 남는다.

- [ ] **Step 6: 커밋하고 PR 본문을 갱신한다**

```bash
gh pr edit 452 --body-file <갱신한 파일>
```

`## 작업 목록` 의 1·2·4 를 체크하고, `## 남은 것` 에서 확정된 숫자 항목을 지운다.

---

## 자체 검토

**Spec 대응:** spec 1절 → Task 1 ✓ · 2절 → Task 2 ✓ · 4절 → Task 3 (상한은 사용자가
버렸고 가로 늘림으로 바뀌었다 — **spec 4절을 이 결정으로 다시 써야 한다**) ·
3·5·6·7절 → Phase B~E.

**빈칸:** 없음. 모든 단계에 실제 코드와 명령이 있다.

**이름 일관성:** `InventoryMetrics.Panel`·`Slot`·`Thumb`·`Label`·`Gap`·`TabRow`·
`PanelPadding`·`SlotPadding` 이 Task 2 의 테스트와 구현에서 같다. `CardSlotHeight` ·
`HomeGameRowHeight` · `BarHeight` · `FabSize` · `FabLift` · `hStretch` ·
`onPickHStretch` 도 같다.

**남은 위험 둘:**

1. **시즌 행 28dp 가 실제로 28dp 가 될지는 확인이 필요하다.** M3 `Button` 은 내부
   `Row` 에 `defaultMinSize(minHeight = 40.dp)` 를 걸는데, 겉의 `Modifier.height(28.dp)`
   가 최대 제약을 28 로 막으므로 28 이 나올 것으로 본다. **안 되면** `TextButton` 을
   `Row` + `clickable` 로 바꾼다 — 그때도 라벨·화살표·`weight` 배치는 그대로 옮긴다.
2. **`WideLayoutTest` 가 깨질 수 있다.** 카드 덩어리 합이 30dp 줄어 `homeScrolls` 경계가
   움직인다. 이건 사용자가 정한 잠금이 아니라 **전제가 바뀐 것**이라, 계산 근거를
   주석에 적고 기대값을 고친다.
