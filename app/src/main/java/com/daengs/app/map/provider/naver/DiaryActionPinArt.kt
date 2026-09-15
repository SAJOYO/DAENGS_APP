package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.walk.WalkMomentType
import kotlin.math.roundToInt

/** Same frame as a scene number. Every behavior silhouette survives a mixed group. */
internal fun diaryActionPinBitmap(context: Context, behaviors: Set<WalkMomentType>, count: Int,
    selected: Boolean, density: Float): Bitmap {
    require(behaviors.isNotEmpty())
    val ordered = WalkMomentType.entries.filter(behaviors::contains)
    val base = diaryPinBitmap("", selected, density, detached = true, minimumWidthDp = ordered.size * 28f + 4f)
    val canvas = Canvas(base)
    ordered.forEachIndexed { index, type ->
        val drawable = requireNotNull(context.getDrawable(when (type) {
            WalkMomentType.SNIFFING -> R.drawable.ic_walk_sniffing
            WalkMomentType.EXCRETION -> R.drawable.ic_walk_excretion
            WalkMomentType.BARKING -> R.drawable.ic_walk_barking
            WalkMomentType.NOTE -> R.drawable.ic_walk_note
        }))
        drawable.setBounds(((3 + index * 28) * density).roundToInt(), (3 * density).roundToInt(),
            ((29 + index * 28) * density).roundToInt(), (29 * density).roundToInt())
        drawable.draw(canvas)
    }
    return if (count > 1) withDiaryCountBadge(base, count.toString(), density) else base
}

@Preview(showBackground = true)
@Composable
private fun DiaryObjectFramesPreview() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(diaryGroupPinBitmap(1,1,false,density,detached=true).asImageBitmap(), "장면")
        Image(diaryActionPinBitmap(context,setOf(WalkMomentType.SNIFFING),1,false,density).asImageBitmap(), "행동")
        Image(diaryActionPinBitmap(context,setOf(WalkMomentType.SNIFFING),12,true,density).asImageBitmap(), "선택한 행동 12건")
        Image(diaryActionPinBitmap(context,setOf(WalkMomentType.SNIFFING,WalkMomentType.BARKING),15,false,density).asImageBitmap(), "서로 다른 행동 15건")
    }
}
