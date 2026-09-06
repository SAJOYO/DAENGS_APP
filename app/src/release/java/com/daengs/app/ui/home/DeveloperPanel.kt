package com.daengs.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.daengs.app.miniroom.DogHerd
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.OutsideView
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.dogcard.CardTemplate

/**
 * 릴리스용 빈 껍데기. **진짜는 `app/src/debug/` 에 있다.**
 *
 * ## 왜 소스셋으로 가르나
 *
 * 예전에는 `if (BuildConfig.DEBUG)` 로 가렸다. 그건 **안 보이게 할 뿐 빼지는
 * 않는다** — R8 이 꺼져 있어(`release { optimization { enable = false } }`)
 * 패널 코드도, 견종 칩 스물일곱 개도, 격자 그리는 코드도 스토어 APK 안에 그대로
 * 들어갔다.
 *
 * 게다가 그 방식은 이미 어긋나 있었다. 토글은 `BuildConfig.DEBUG` 로 감쌌는데
 * **여섯 줄 아래 패널은 안 감쌌다.** 호출부가 늘 때마다 잊어버릴 자리가 는다.
 *
 * 소스셋으로 가르면 잊어버리는 순간 **빨간 빌드가 되지, 출시되지 않는다.**
 *
 * ## 고칠 때 지킬 것
 *
 * **시그니처가 debug 쪽과 한 글자도 달라지면 안 된다.** 어긋나면 `assembleRelease`
 * 에서만 깨지므로, 확인 절차에 릴리스 빌드가 들어 있다.
 *
 * 그리고 **아무것도 그리면 안 된다.** 빈 `Box` 라도 두면 릴리스에서 인벤토리
 * 버튼 아래에 설명할 수 없는 여백이 생긴다.
 */
@Composable
fun DeveloperToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) = Unit

@Composable
fun DeveloperPanel(
    state: MiniRoomState,
    herd: DogHerd?,
    breedOverride: DogBreed?,
    onPickBreed: (DogBreed?) -> Unit,
    profileBreed: DogBreed,
    onPickProfile: (DogBreed) -> Unit,
    outside: OutsideView,
    onPickOutside: (OutsideView) -> Unit,
    onOpenCutoutLab: (() -> Unit)? = null,
    onMakeCard: ((CardTemplate) -> Unit)? = null,
    canMakeCard: Boolean = false,
    onPickProfilePhoto: (() -> Unit)? = null,
    onClearProfilePhoto: (() -> Unit)? = null,
    hasProfilePhoto: Boolean = false,
    onPickDevPets: ((Int) -> Unit)? = null,
    devPetCount: Int = 0,
    onToggleEmptyRoom: (() -> Unit)? = null,
    emptyRoom: Boolean = false,
    modifier: Modifier = Modifier,
) = Unit
