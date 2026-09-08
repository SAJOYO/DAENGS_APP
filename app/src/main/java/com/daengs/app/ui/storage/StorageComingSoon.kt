package com.daengs.app.ui.storage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
/**
 * 저장소 탭. **아직 없는 기능이라고 말해 주는 자리다.**
 *
 * 빈 화면을 두지 않는다 — 눌렀는데 아무것도 없으면 고장으로 읽힌다. 무엇이 올
 * 자리인지 한 줄로 말해 두면 기다릴 수 있다.
 *
 * **여기서 "곧" 이나 날짜를 말하지 않는다.** 사진·영상을 어디에 남길지가 아직
 * 정해지지 않아서(저장소 논의) 언제가 될지 우리도 모른다. 지키지 못할 약속을
 * 화면에 적으면 그게 다음 사람의 부채가 된다.
 */
@Composable
fun StorageComingSoon(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DaengsIconView(DaengsIcon.Camera, Modifier.size(44.dp), tint = PinkSoft)
        Spacer(Modifier.height(18.dp))
        Text("저장소는 준비 중이에요", color = TextDark, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "찍은 사진과 영상을 여기 모아 볼 수 있게 만들고 있어요.",
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}
/**
 * [StorageComingSoon] 의 작은 판. 저장소 탭에 케어 기록과 대화 보관함이 들어온 뒤로는 탭 전체가
 * "준비 중" 이 아니라 **사진·영상만** 준비 중이다 — 목록 맨 밑에 한 줄로 남긴다.
 * 날짜를 말하지 않는 이유는 위와 같다.
 */
@Composable
fun StoragePhotosNotice(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DaengsIconView(DaengsIcon.Camera, Modifier.size(28.dp), tint = PinkSoft)
        Spacer(Modifier.height(10.dp))
        Text("사진과 영상은 준비 중이에요", color = TextDark, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "찍은 사진과 영상을 여기 모아 볼 수 있게 만들고 있어요.",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun StoragePhotosNoticePreview() = DaengsTheme { StoragePhotosNotice() }

@Preview(widthDp = 411, heightDp = 640, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun StorageComingSoonPreview() {
    DaengsTheme {
        Column(Modifier.fillMaxSize()) { StorageComingSoon() }
    }
}
