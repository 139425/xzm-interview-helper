"""Build a private bootstrap snapshot; never commit generated personal data.

Usage: python scripts/build_experience_seed.py --library /path/to/export --output /private/seed.json
"""
import argparse
import hashlib
import json
from pathlib import Path


def build(root: Path):
    reports = sorted((root / "面经题库").glob("*/报告数据.json"))
    if not reports:
        raise ValueError("No versioned interview report found")
    report = json.loads(reports[-1].read_text())
    metadata = {str(x["id"]): x for x in json.loads((root / "原始数据/导出清单.json").read_text())}
    rows = {}
    for row in report["rows"]:
        rows.setdefault(row["doc_id"], []).append({"id": row["id"], "line": row["line"], "text": row["raw"], "keys": row["keys"]})
    docs = []
    for doc in report["docs"]:
        path = root / doc["relative_path"]
        body = path.read_text()
        if hashlib.sha256(path.read_bytes()).hexdigest() != doc["sha256"]:
            raise ValueError(f"Source changed since report: {doc['doc_id']}")
        docs.append({"id": doc["doc_id"], "title": doc["title"], "stage": doc["stage"], "url": doc["url"],
                     "body": body, "hash": doc["sha256"], "active": True, "rows": rows.get(doc["doc_id"], []),
                     "remoteVersion": metadata.get(doc["doc_id"], {}).get("content_updated_at", "")})
    questions = [{**{k: q[k] for k in ("key", "id", "question", "category", "group")}, "reviewed": True} for q in report["questions"]]
    knowledge = []
    for path in (root / "文档").rglob("index.md"):
        if "面经__234041981" in path.parts or "提示词__245428804" in path.parts:
            continue
        body = path.read_text()
        if len(body.split("---", 1)[-1].strip()) > 150:
            knowledge.append({"title": path.parent.name.split("__")[0], "body": body[:30000]})
    return {"schemaVersion": 1, "docs": docs, "questions": questions, "chains": report["chains"], "knowledge": knowledge}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--library", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = build(args.library)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False))
    args.output.chmod(0o600)
    print({"documents": len(data["docs"]), "questions": len(data["questions"]), "knowledge": len(data["knowledge"])})
