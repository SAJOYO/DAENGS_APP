package com.daengs.app.ui.places.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.places.placeMarkerId
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.map.features.places.PlaceCategoryIcon
import com.daengs.app.map.features.places.toCardPresentation
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.places.PlaceBookmarkButton
import kotlinx.serialization.json.*

private fun PlaceSearchHit.registration(): Pair<String, String> {
    val access = place.facts.petAccess
    return when (if (access?.dogOk == false) false else access?.allowed) {
        true -> "✓" to "동반 가능 등록"
        false -> "×" to "동반 불가 등록"
        null -> "?" to "동반 여부 확인 필요"
    }
}

@Composable
internal fun PlaceResultRow(hit: PlaceSearchHit, selected: Boolean, onOpen: () -> Unit,
    saved: Boolean? = null, onBookmark: () -> Unit = {}, bookmarkEnabled: Boolean = true,
    bookmarkKnown: Boolean = true, showDistance: Boolean = true) {
    val (mark, registration) = hit.registration()
    Surface(onClick = onOpen, color = DaengsColors.Surface,
        modifier = Modifier.fillMaxWidth().testTag("place-result-${placeMarkerId(hit.place.key)}")) {
        Row(Modifier.heightIn(min = 108.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceCategoryIcon(hit.place.match.kind, Modifier.size(22.dp), DaengsColors.TextSecondary)
                    Text(hit.place.name, modifier = Modifier.weight(1f), fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = DaengsColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (showDistance) hit.toCardPresentation().meta else categoryLabel(hit.place.match.kind) + " · 거리 미확인", fontSize = 12.sp, color = DaengsColors.TextSecondary)
                Text("$mark $registration", fontSize = 12.sp, color = DaengsColors.TextPrimary,
                    modifier = Modifier.semantics { contentDescription = registration })
            }
            if (saved != null) PlaceBookmarkButton(hit.place.name, saved, onBookmark, bookmarkEnabled, bookmarkKnown)
            else Text("›", fontSize = 24.sp, color = if (selected) DaengsColors.BrandPrimary else DaengsColors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceDetailSheet(hit: PlaceSearchHit, onDismiss: () -> Unit,
    onAction: (String) -> Unit, actions: (@Composable (PlaceSearchHit) -> Unit)?, dogNames: Map<String, String>,
    saved: Boolean? = null, onBookmark: () -> Unit = {}, bookmarkEnabled: Boolean = true,
    bookmarkKnown: Boolean = true, showDistance: Boolean = true) {
    key(hit.place.key) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DaengsColors.Surface, contentColor = DaengsColors.TextPrimary,
            modifier = Modifier.testTag("place-detail-sheet")) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .72f).dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("‹ 목록으로") }
                    Spacer(Modifier.weight(1f))
                    if (saved != null) PlaceBookmarkButton(hit.place.name, saved, onBookmark, bookmarkEnabled, bookmarkKnown)
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    PlaceDetailContent(hit, onAction, actions, dogNames, showDistance)
                }
            }
        }
    }
}

@Composable
internal fun PlaceDetailContent(hit: PlaceSearchHit, onAction: (String) -> Unit = {},
    actions: (@Composable (PlaceSearchHit) -> Unit)? = null, dogNames: Map<String, String> = emptyMap(), showDistance: Boolean = true) {
    val p = hit.place
    val presentation = hit.toCardPresentation()
    val (mark, registration) = hit.registration()
    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(if (showDistance) presentation.meta else categoryLabel(p.match.kind) + " · 거리 미확인", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = DaengsColors.TextSecondary)
    Text("$mark $registration", Modifier.padding(vertical = 16.dp).semantics { contentDescription = registration }, fontSize = 14.sp)
    HorizontalDivider(Modifier.padding(bottom = 16.dp))
    if (hit.evaluations.dogs.isNotEmpty()) {
        Text("선택한 반려견 기준", fontSize = 13.sp)
        hit.evaluations.dogs.forEach { evaluation ->
            Text("${dogNames[evaluation.ref] ?: "반려견"} · ${com.daengs.app.ui.places.dogEvaluationLabel(evaluation)}", fontSize = 12.sp)
        }
    }
    Text("추가 동반 조건", Modifier.padding(top = 8.dp), fontSize = 13.sp)
    val raw = p.facts.restrictions?.get("raw")?.jsonPrimitive?.contentOrNull
        ?: p.facts.petAccess?.raw?.get("restrictions")?.jsonPrimitive?.contentOrNull
    val restrictionEvaluation = hit.evaluations.restrictions
    val chips = (restrictionEvaluation?.get("chips") as? JsonArray)
        ?: (p.facts.restrictions?.get("chips") as? JsonArray)
    chips?.forEach { chip ->
        (chip as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull?.let { Text(it, fontSize = 12.sp) }
    }
    restrictionEvaluation?.get("state")?.jsonPrimitive?.contentOrNull?.let { status ->
        if (status != "compatible") Text(if (status == "incompatible") "추가 동반 조건 불일치" else "추가 동반 조건 확인 필요", fontSize = 12.sp)
    }
    Text(raw?.takeIf { it.isNotBlank() } ?: if (p.facts.restrictions?.get("state")?.jsonPrimitive?.contentOrNull == "none_confirmed") "추가 제한 없음으로 등록" else "제한 정보 없음 · 확인 필요", fontSize = 12.sp)
    Text("허용 크기: ${p.facts.petAccess?.raw?.get("size")?.jsonPrimitive?.contentOrNull ?: "정보 없음"}", fontSize = 12.sp)
    hit.evaluations.dogAccess?.let { Text(when (it.state) { DogAccessState.COMPATIBLE -> "크기·체중 조건상 가능"; DogAccessState.INCOMPATIBLE -> "크기·체중 조건 불일치"; DogAccessState.UNKNOWN -> "크기·체중 조건 확인 필요" }, fontSize = 12.sp) }
    Text("${p.key.source} · ${p.classifications.firstOrNull()?.asOf ?: "날짜 미상"}\n현재 동반 정책은 방문 전 확인해 주세요.", Modifier.padding(vertical = 10.dp), fontSize = 10.sp, color = DaengsColors.TextSecondary)
    p.fieldSources["facts.restrictions"]?.let { source ->
        Text("추가 조건 출처: ${source.source.source} · ${source.asOf ?: "날짜 미상"}", fontSize = 10.sp)
    }
    presentation.parking?.let { Text(it.text, fontSize = 12.sp) }
    p.facts.hoursText?.let { Text(it, fontSize = 12.sp) }
    p.facts.address?.let { Text(it, fontSize = 12.sp) }
    if (actions != null) actions(hit) else {
    p.facts.phone?.let { TextButton(onClick = { onAction("전화는 실제 앱 연결 단계에서 확인합니다.") }) { Text("전화로 확인") } }
    TextButton(onClick = { onAction("길찾기는 실제 앱 연결 단계에서 확인합니다.") }) { Text("길찾기") }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ResultRowPreview() { DaengsTheme { PlaceResultRow(PreviewPlaceHit(), true, {}) } }

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DetailContentPreview() { DaengsTheme { Column(Modifier.background(DaengsColors.Surface).padding(20.dp)) { PlaceDetailContent(PreviewPlaceHit()) } } }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DetailSheetPreview() { DaengsTheme { PlaceDetailSheet(PreviewPlaceHit(), {}, {}, null, emptyMap()) } }
