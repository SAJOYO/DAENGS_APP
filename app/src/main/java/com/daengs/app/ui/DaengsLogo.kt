package com.daengs.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

/**
 * 글자 로고. 상단바와 랜딩 화면이 같이 쓴다.
 *
 * 원래 `DaengsTopBar.kt` 안에 private 으로 있었는데, 랜딩 화면이 같은 로고를 써야 해서
 * 꺼냈다. 그림 파일은 없다 — 런처 아이콘 말고는 로고 원화가 없어서 글자로 짠 것이다.
 *
 * @param scale 1 이 상단바 크기. 랜딩에서는 크게 쓴다.
 */
@Composable
fun DaengsLogo(modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "댕스",
                color = DaengPinkDeep,
                fontWeight = FontWeight.Black,
                fontSize = (21 * scale).sp,
            )
            DaengsIconView(
                DaengsIcon.Paw,
                Modifier.size((13 * scale).dp).padding(top = (1 * scale).dp),
                tint = DaengPink,
            )
        }
        Text(
            "DAENGS",
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = (8 * scale).sp,
            letterSpacing = (2.6 * scale).sp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun DaengsLogoPreview() {
    DaengsTheme {
        Row(verticalAlignment = Alignment.Bottom) {
            DaengsLogo()
            DaengsLogo(Modifier.padding(start = 20.dp), scale = 2.2f)
        }
    }
}
