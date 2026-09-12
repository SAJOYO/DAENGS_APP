# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Render a private observation-connectivity report as a reviewable Markdown table (no network)."""
import argparse
import itertools
import json
from pathlib import Path


def table(headers, rows):
    def cell(value):
        return str(value if value is not None else "—").replace("|", "\\|").replace("\n", " ")
    return ["| " + " | ".join(map(cell, headers)) + " |", "|" + "---|" * len(headers)] + [
        "| " + " | ".join(map(cell, row)) + " |" for row in rows
    ]


def render(payload):
    report = payload["observation_connectivity"]
    if report["format"] != "walk-observation-connectivity-review-v1" or report["stage"] != "review_candidate":
        raise ValueError("Unsupported review format or adoption state")
    lines = ["# 관측 연결 검토 후보", "", "기존 보행 집계와 지도에는 채택하지 않은 평가다.", "",
             f"- reader: `{report['reader_version']}`",
             f"- 정책: `{report['policy']['version']}`",
             f"- 입력 범위: `{report['input_scope']}`",
             f"- 속도 근거: `{report['speed_source']}`",
             f"- 지원하지 못한 이유: {report['unsupported_reason'] or '없음'}",
             f"- 원본 SHA-256: `{payload['detail_sha256']}`",
             f"- 장면 SHA-256: `{payload['storyboard_sha256']}`",
             f"- 기존 거리/활동시간: {payload['distance_m']}m / {payload['active_duration_ms']}ms", "",
             "## 후보 정책", ""]
    lines += table(["설정", "값"], report["policy"].items())
    lines += ["", "위치 정확도 반경을 더한 allowance는 휴리스틱이다. 통계적 오차 상한이 아니다.",
              "좌표 속도는 원본 이웃의 거리/시간이며 기기 제공 speed나 기존 마지막 수용점 기준 속도와 다르다.", "",
              "## 집계와 후보", ""]
    lines += table(["항목", "개수"], report["summary"].items())
    lines += ["", "INCLUDED는 기존 수용 연결이 원본 범위를 소유한다는 뜻이다. 그 안의 모든 원본 선분을",
              "보행으로 인정하거나 각 선분 길이를 집계했다는 뜻은 아니다. 거리 기여는 owners에 한 번만 있다.",
              "OWNERSHIP_CONFLICT는 기존 소유 범위와 관측 연결/제외 근거가 다른 부분이며 보조선을 만들지 않는다.", "",
              "## 보조 경로 후보", ""]
    lines += table(["인덱스", "원본 범위", "보행 집계", "관측 수"],
                   [(i, f"{r['from_seq']} → {r['to_seq']}", r["walking_use"], len(r["source_seqs"]))
                    for i, r in enumerate(report["auxiliary_runs"])])
    lines += ["", "연결 후보는 위치의 연속성을 뜻하며 정지/이동 분류나 방향 화살표의 허가가 아니다.",
              "", "## 장면의 시간과 위치 근거", ""]
    lines += table(["장면", "원본 seq", "위치 근거 검증", "위치 품질", "사건 대비 위치 시각", "연관 후보 인덱스"],
                   [(s["scene"], s["observation_seq"], s["source_verified"], s["position_quality"],
                     s["location_relation_to_event"], s["candidate_run_indexes"]) for s in report["scenes"]])
    lines += ["", "BEFORE_EVENT/AFTER_EVENT는 사건 당시 위치로 바꿔 해석하지 않는다. 후보 인덱스는 이 보고서에서만 유효하다.",
              "", "## 원본 구간 판정", "", "동일한 판정·소유자가 이어지는 범위만 묶었다. 원본별 수치는 JSON에 있다.", ""]
    def signature(row):
        return (row["connection"], row["walking_use"], row["auxiliary_use"], row["owner_to_seq"], row["clock"],
                tuple(row["connection_reasons"]), tuple(row["accounting_reasons"]))
    rows = []
    for key, group in itertools.groupby(report["intervals"], signature):
        group = list(group)
        rows.append((f"{group[0]['from_seq']} → {group[-1]['to_seq']}", key[0], key[1], key[2], key[3],
                     ", ".join(key[5] + key[6])))
    lines += table(["범위", "관측 연결", "집계", "보조 경로", "소유 연결 끝 seq", "근거"], rows)
    lines += ["", "## 단일 설정 변경 대조", "",
              "각 행은 기본 정책에서 설정 하나만 바꾼 결과다. 결과가 바뀌지 않아도 실세계 정확성을 보장하지 않는다.", ""]
    lines += table(["변경", "바뀐 구간 수", "최초 차이 from seq", "연결 구간 수", "보조 후보 범위"],
                   [(s["variation"], s["changed_interval_count"], s["first_changed_from_seq"], s["summary"]["CONNECTED"],
                     ", ".join(f"{r['from_seq']}→{r['to_seq']}" for r in s["auxiliary_runs"])) for s in report["sensitivity"]])
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or args.report.with_name(args.report.stem + "-connectivity.md")
    if output.resolve() == args.report.resolve():
        raise ValueError("Do not overwrite the source report")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(json.loads(args.report.read_text(encoding="utf-8"))), encoding="utf-8")
    print(output.resolve())
