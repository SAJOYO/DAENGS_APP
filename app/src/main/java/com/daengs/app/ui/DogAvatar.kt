package com.daengs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft

/**
 * 강아지 얼굴 아바타.
 *
 * 한때는 여기서 원과 타원으로 얼굴을 그렸다. 저쪽 레포가 견종마다 얼굴 그림
 * (`assets/dogs/<견종>/portrait.png`) 을 따로 그려 두면서 그럴 이유가 없어졌다.
 *
 * 그림은 **화풍이 방 안 강아지와 다르다.** 방 안은 도트, 이쪽은 사실풍이다.
 * 저쪽이 처음부터 그렇게 나눠 그렸다. 32dp 까지 줄어드는 자리라 도트로는 눈코가
 * 뭉개지는데, 사실풍 얼굴은 작아져도 견종이 남는다.
 *
 * 원본이 불투명한 정사각형이라 [CircleShape] 로 자르고 테두리를 한 겹 두른다.
 * 안 두르면 크림색 배경이 밝은 카드 위에서 경계 없이 번진다.
 *
 * @param face 같은 견종의 **어느 판**으로 그릴지. **함수를 두 벌로 나누지 않는다** —
 *   원으로 자르고 테두리를 두르는 규칙이 갈리면 얼굴마다 경계가 달라진다.
 *   바뀌는 것은 그림 하나뿐이다
 */
@Composable
fun DogAvatar(
    breed: DogBreed,
    modifier: Modifier = Modifier,
    face: DogFace = DogFace.Portrait,
) {
    Image(
        painter = painterResource(face.resOf(breed)),
        contentDescription = breed.label,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, PinkSoft, CircleShape),
    )
}

/**
 * 같은 견종의 어느 얼굴인가.
 *
 * 셋 다 256x256 불투명 정사각형이고, 원으로 자르는 규칙도 같다 — **다른 것은 그림뿐**
 * 이라 값 하나로 고른다. 불리언을 여럿 두면 둘 다 켜진 상태가 생긴다.
 */
enum class DogFace {
    /** 프로필. 상단바·마이·견종 고르기가 쓴다. */
    Portrait,

    /** 챗봇 얼굴. 학사모 쓴 "똑똑이" 판이라 **내 개는 아니지만 남의 개도 아니다**. */
    Smart,

    /** 답을 만드는 동안. 앞발을 턱에 댄 "곰곰이" 판이다. */
    Thinking,
    ;

    fun resOf(breed: DogBreed): Int = when (this) {
        Portrait -> breed.portraitRes
        Smart -> breed.smartRes
        Thinking -> breed.thinkingRes
    }
}

/**
 * 그림이 없는 견종(믹스)의 얼굴.
 *
 * **아무 견종 얼굴이나 갖다 쓰지 않는다.** 그러면 사용자는 자기 개가 아닌 얼굴을
 * 보게 된다. 발자국은 "우리가 이 아이 얼굴을 모른다"는 사실을 그대로 말한다.
 *
 * 마이 탭의 강아지 카드와 등록 폼의 견종 고르기가 **같은 그림**을 써야 해서 여기 둔다.
 */
@Composable
fun PawAvatar(modifier: Modifier = Modifier, size: Dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(PinkFaint),
        contentAlignment = Alignment.Center,
    ) {
        DaengsIconView(DaengsIcon.Paw, Modifier.size(size * 0.5f), tint = DaengPink)
    }
}

/** 챗봇 얼굴. 학사모가 원에 잘리지 않는지, 안경이 뭉개지지 않는지 본다. */
@Preview
@Composable
private fun SmartDogAvatarPreview() {
    Row {
        listOf(DogBreed.BEAGLE, DogBreed.SHIBA_INU_BEIGE, DogBreed.BORDER_COLLIE).forEach {
            DogAvatar(it, Modifier.size(56.dp), face = DogFace.Smart)
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** 32dp 은 말풍선 얼굴 크기다. **작게 봐야 학사모가 뭉개지는 것이 보인다.** */
@Preview
@Composable
private fun SmartDogAvatarBubbleSizePreview() {
    Row {
        DogBreed.ALL.take(8).forEach {
            DogAvatar(it, Modifier.size(32.dp), face = DogFace.Smart)
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Preview
@Composable
private fun DogAvatarPreview() {
    Row {
        listOf(DogBreed.BEAGLE, DogBreed.SHIBA_INU_BEIGE, DogBreed.BORDER_COLLIE).forEach {
            DogAvatar(it, Modifier.size(56.dp))
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** 32dp 은 상단바 크기다. 이만큼 줄여도 견종이 구분되는지 보는 미리보기. */
@Preview
@Composable
private fun DogAvatarTopBarSizePreview() {
    Row {
        DogBreed.ALL.take(8).forEach {
            DogAvatar(it, Modifier.size(32.dp))
            Spacer(Modifier.width(4.dp))
        }
    }
}
