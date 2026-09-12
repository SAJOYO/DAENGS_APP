package com.daengs.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daengs.app.ui.*
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.theme.DaengPink

/** Read-only, local examples. No walk, location, camera, or game repository enters this boundary. */
@Composable
internal fun TerritoryGameRulesDialog(onDismiss: () -> Unit) {
    // The dialog owns another saveable registry. Keep progress in the caller's registry.
    val state = rememberTerritoryRulesState()
    Dialog(onDismissRequest = { if (state.tab == -1) onDismiss() else state.tab = -1 },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center) {
            TerritoryGameRulesContent(onDismiss, Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .heightIn(max = 760.dp).then(if (state.tab == -1) Modifier else Modifier.fillMaxHeight()), state)
        }
    }
}

@Composable
internal fun TerritoryGameRulesContent(onDismiss: () -> Unit, modifier: Modifier = Modifier,
                                      state: TerritoryRulesState = rememberTerritoryRulesState()) {
    val tab = state.tab
    val scenario = state.scenario
    val page = state.page
    val step = scenario.steps[page]
    val advance = { state.page = if (page == scenario.steps.lastIndex) 0 else page + 1 }
    Surface(modifier.testTag("game-rules-dialog").semantics { paneTitle = "점령 규칙" },
        shape = RoundedCornerShape(24.dp), color = CreamBg) {
        // 🔒 **여백은 줄이지 않는다** — `docs/design-locks.md` 3절. 제목이 창 윗변에 붙고(위 8dp)
        //    글이 좌우 끝에 붙어(16dp) 사용자가 실기기에서 "꽉 낀다" 고 했다 (2026-09-12).
        //    잠금 테스트: `TerritoryRulesSpacingLockTest`.
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (tab != -1) IconButton(onClick = { state.tab = -1 },
                    modifier = Modifier.testTag("game-rules-summary-back").semantics { contentDescription = "규칙 요약으로 돌아가기" }) {
                    DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(22.dp).rotate(180f), TextDark)
                }
                Column(Modifier.weight(1f)) {
                    Text(when (tab) { -1 -> "점령 규칙"; 0 -> "직접 해보기"; else -> "자세한 규칙" },
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text("첫 시즌", fontSize = 11.sp, color = TextDark.copy(alpha = .76f))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("game-rules-close")
                    .semantics { contentDescription = "점령 규칙 닫기" }) {
                    DaengsIconView(DaengsIcon.Close, Modifier.size(22.dp), TextDark)
                }
            }
            if (tab >= 1) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).selectableGroup()) {
                listOf("점수", "영역 유지", "시즌").forEachIndexed { position, title ->
                    val index = position + 1
                    Box(Modifier.weight(1f).background(if (tab == index) TextDark else CreamBg, RoundedCornerShape(12.dp))
                        .testTag("game-rules-tab-$index")
                        .selectable(tab == index, role = Role.Tab, onClick = { state.tab = index })
                        .heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, color = if (tab == index) CardWhite else TextDark)
                    }
                }
            }
            // A new step starts at its illustration, even after scrolling a long rule or large text.
            key(tab, scenario, page) {
                Column(Modifier.weight(1f, fill = tab != -1).fillMaxWidth().testTag("game-rules-body")
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    when (tab) {
                        -1 -> TerritoryRulesSummary()
                        0 -> {
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuideScenario.entries.forEach { option ->
                                    FilterChip(selected = scenario == option, onClick = { state.scenario = option; state.page = 0 },
                                        label = { Text(option.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(containerColor = CardWhite,
                                            selectedContainerColor = PinkSoft, labelColor = TextDark, selectedLabelColor = TextDark),
                                        modifier = Modifier.weight(1f).testTag("game-guide-${option.name}"))
                                }
                            }
                            Text("직접 눌러보는 예시 · 실제 점령·점수에는 영향이 없어요", color = TextDark.copy(alpha = .76f),
                                fontSize = 11.sp, lineHeight = 16.sp)
                            TerritoryGuideScene(scenario, step, onSelectPole = if (step == GuideStep.SELECT) advance else null)
                            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${page + 1} / ${scenario.steps.size} · ${step.title(scenario)}", fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.testTag("game-guide-step"))
                                Text(step.detail(scenario), fontSize = 13.sp, lineHeight = 21.sp, color = TextDark)
                            }
                        }
                        1 -> {
                            RuleHighlight("20 + 80 = 100점", "같은 곳에서 미인증 점령 후 사진 인증한 예시예요.")
                            firstSeasonGameRules.filter { it.id in setOf("mark", "takeover", "hold") }.forEach { RuleExplanation(it) }
                        }
                        2 -> {
                            RuleHighlight("최대 72시간", "다시 방문해 유지 시간을 연장해요. 연장 보상은 0점이에요.")
                            RuleExplanation(firstSeasonGameRules.first { it.id == "renew" })
                            RuleHighlight("인증 직후 10분 보호", "보호 중인 다른 주인의 영역에는 도전할 수 없어요. 남은 시간을 확인해 주세요.")
                        }
                        3 -> {
                            RuleHighlight("매월 1일 00:00", "한국 시간 기준 · 결산 후 다음 시즌이 시작돼요.")
                            RuleExplanation(firstSeasonGameRules.first { it.id == "season" })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            HorizontalDivider(color = PinkSoft)
            Row(Modifier.fillMaxWidth().testTag("game-rules-footer").padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == -1) {
                    Button(onClick = { state.tab = 0 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("game-rules-example-open"), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DaengPink)) {
                        Text("직접 해보기", fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(onClick = { state.tab = 1 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("game-rules-details-open"), shape = RoundedCornerShape(14.dp)) {
                        Text("자세한 규칙", color = TextDark, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else if (tab == 0) {
                    TextButton(onClick = { state.page-- }, enabled = page > 0,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextDark),
                        modifier = Modifier.testTag("game-guide-previous")) { Text("이전") }
                    Button(onClick = advance, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("game-guide-next"),
                        colors = ButtonDefaults.buttonColors(containerColor = DaengPink), shape = RoundedCornerShape(14.dp)) {
                        Text(step.action(scenario), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaengPink), shape = RoundedCornerShape(14.dp)) { Text("확인") }
            }
        }
    }
}

@Stable
internal class TerritoryRulesState(tab: Int = -1, scenario: GuideScenario = GuideScenario.EMPTY, page: Int = 0) {
    var tab by mutableIntStateOf(tab)
    var scenario by mutableStateOf(scenario)
    var page by mutableIntStateOf(page)
}

@Composable
private fun rememberTerritoryRulesState(): TerritoryRulesState = rememberSaveable(saver = listSaver(
    save = { listOf(it.tab, it.scenario.name, it.page) },
    restore = { TerritoryRulesState(it[0] as Int, GuideScenario.valueOf(it[1] as String), it[2] as Int) },
)) { TerritoryRulesState() }

@Composable
private fun RuleHighlight(title: String, detail: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = PinkSoft) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(detail, fontSize = 13.sp, lineHeight = 20.sp, color = TextDark)
        }
    }
}

@Composable
private fun RuleExplanation(rule: GameRule) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardWhite) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rule.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(rule.summary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            Text(rule.detail, fontSize = 13.sp, lineHeight = 21.sp, color = TextDark)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun TerritoryGameRulesPreview() = DaengsTheme {
    TerritoryGameRulesContent({}, Modifier.fillMaxSize())
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun TerritoryGameExamplePreview() = DaengsTheme {
    TerritoryGameRulesContent({}, Modifier.fillMaxSize(), TerritoryRulesState(tab = 0))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun TerritoryGameRuleDetailsPreview() = DaengsTheme {
    TerritoryGameRulesContent({}, Modifier.fillMaxSize(), TerritoryRulesState(tab = 1))
}
