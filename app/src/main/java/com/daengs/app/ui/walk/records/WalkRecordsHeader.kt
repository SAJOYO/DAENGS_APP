package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.records.WalkRecordsQuery
import java.time.LocalDate

/** Both views share one selection, one condition entry point and the same header geometry. */
@Composable
internal fun WalkRecordsHeader(
    query: WalkRecordsQuery, pets: List<Pet>, overview: Boolean, behavior: WalkMomentType?,
    onBack: () -> Unit, onOverview: (Boolean) -> Unit, onConditions: () -> Unit,
    today: LocalDate = LocalDate.now(),
    /** 보호자 조건 요약("모든 보호자"·"키키 외 1명"). null 이면(공동 조회를 못 쓰는 화면) 칸을 두지 않는다. */
    carerLabel: String? = null,
) {
    val dogLabel = query.dogIds?.let { ids ->
        if (ids.size == 1) pets.firstOrNull { it.id in ids }?.name ?: "선택한 강아지" else "${ids.size}마리"
    } ?: "모든 강아지"
    val periodLabel = when {
        query.filter.from == null && query.filter.through == null -> "전체 기간"
        query.filter.through == today && query.filter.from == today.minusDays(6) -> "최근 7일"
        query.filter.through == today && query.filter.from == today.minusDays(29) -> "최근 30일"
        else -> "날짜 지정"
    }
    val summary = buildList {
        add(dogLabel); carerLabel?.let { add(it) }; add(periodLabel)
        behavior?.let { add(it.label) }
        if (query.filter.keyword.isNotBlank()) add(query.filter.keyword)
        addAll(query.filter.seasons.sortedBy { it.ordinal }.map { it.label })
        addAll(query.filter.weather.sortedBy { it.ordinal }.map { it.label })
    }.joinToString(" · ")
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().testTag("records-header")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "뒤로" }) {
                    DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(24.dp).rotate(180f), MaterialTheme.colorScheme.onSurface)
                }
                Text("산책 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Surface(onClick = onConditions, color = PinkFaint, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("records-conditions")) {
                Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(summary, Modifier.weight(1f).testTag("records-active-filters"), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text("조건", style = MaterialTheme.typography.labelMedium)
                    DaengsIconView(DaengsIcon.CaretDown, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                listOf(false to "산책별", true to "모아보기").forEach { (isOverview, label) ->
                    Tab(selected = overview == isOverview, onClick = { onOverview(isOverview) },
                        modifier = Modifier.weight(1f).testTag(if (isOverview) "records-view-overview" else "records-view-walks")) {
                        Text(label, Modifier.padding(vertical = 13.dp), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = if (overview == isOverview) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(thickness = 3.dp, color = if (overview == isOverview) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun RecordsHeaderPreview() {
    DaengsTheme { WalkRecordsHeader(WalkRecordsQuery(), recordsPreviewPets(), false, null, {}, {}, {}) }
}
