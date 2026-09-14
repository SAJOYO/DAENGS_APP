package com.daengs.app.ui.places

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/** 얼굴과 인식표를 한 버튼으로 묶어 이름표 아래까지 같은 대화 입구로 사용한다. */
@Composable
internal fun PlaceDogAssistantEntry(
    avatarBreed: DogBreed? = null,
    avatarPhoto: Bitmap? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier.widthIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "검색 대화 열기", onClick = onClick)
            .semantics { contentDescription = "강아지에게 검색 조건 말하기" }
            .testTag("place-dog-anchor"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            Modifier.size(48.dp), shape = CircleShape, shadowElevation = 4.dp,
            color = DaengsColors.Surface, border = BorderStroke(1.dp, DaengsColors.BrandPrimarySoft),
        ) {
            val portrait = Modifier.padding(3.dp).fillMaxSize().clip(CircleShape)
            if (avatarPhoto != null) Image(avatarPhoto.asImageBitmap(), contentDescription = null,
                modifier = portrait, contentScale = ContentScale.Crop)
            else Image(painterResource((avatarBreed ?: DogBreed.BEAGLE).portraitRes), contentDescription = null,
                modifier = portrait, contentScale = ContentScale.Crop)
        }
        // 인식표 고리는 얼굴 아래 여백 안에만 그려 얼굴이나 지도 조작을 가리지 않는다.
        Canvas(Modifier.size(6.dp, 3.dp)) {
            drawOval(DaengsColors.TextSecondary, style = Stroke(1.dp.toPx()))
        }
        Surface(
            shape = RoundedCornerShape(7.dp), color = DaengsColors.BrandPrimarySoft,
            border = BorderStroke(1.dp, DaengsColors.BrandPrimary),
        ) {
            Text("도우미견", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color = DaengsColors.TextPrimary, fontSize = 11.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun PlaceDogAssistantEntryPreview() {
    DaengsTheme { Box(Modifier.padding(12.dp)) { PlaceDogAssistantEntry {} } }
}
