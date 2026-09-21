export const masteryLabels = {
  UNKNOWN: "未验证",
  PROMPTED: "需要提示",
  INDEPENDENT: "独立回答",
  TRANSFER: "通过变式",
  RETESTED: "复测通过",
};

export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function priority(
  question,
  progress = {},
  role = "",
  today = localDate(),
) {
  let score = question.frequency || 0;
  const reasons = [];
  if (progress.pinned) {
    score += 30;
    reasons.push("已加入计划");
  }
  if (progress.dueAt && String(progress.dueAt).slice(0, 10) <= today) {
    score += 40;
    reasons.push("到期复测");
  }
  if (progress.state === "PROMPTED") {
    score += 20;
    reasons.push("上次需要提示");
  }
  if (!progress.state || progress.state === "UNKNOWN") {
    score += 8;
    reasons.push("先做诊断");
  }
  const text =
    `${question.group} ${question.category} ${question.question}`.toLowerCase();
  const relevant =
    role &&
    (text.includes(role.toLowerCase()) ||
      (/sre|运维/i.test(role) &&
        /sre|网络|操作系统|计算机基础|容器|排障/.test(text)) ||
      (/java|后端/i.test(role) && /java|数据库|中间件|并发/.test(text)) ||
      (/ai|人工智能/i.test(role) && /ai|rag|mcp/.test(text)));
  if (relevant) {
    score += 20;
    reasons.push("目标方向相关");
  }
  if (
    progress.state === "RETESTED" &&
    !(progress.dueAt && String(progress.dueAt).slice(0, 10) <= today)
  )
    score -= 25;
  return { score, reasons: reasons.length ? reasons : ["历史题频"] };
}

export function filterQuestions(
  questions,
  { search = "", category = "", stage = "", pending = false } = {},
) {
  const term = search.trim().toLowerCase();
  return questions.flatMap((q) => {
    const evidence = (q.evidence || []).filter(
      (e) => !stage || e.stage === stage,
    );
    if (
      !evidence.length ||
      (category && q.group !== category) ||
      (pending && q.reviewed !== false)
    )
      return [];
    if (
      term &&
      !`${q.question} ${q.category} ${evidence.map((e) => `${e.title} ${e.row.text}`).join(" ")}`
        .toLowerCase()
        .includes(term)
    )
      return [];
    return [
      {
        ...q,
        evidence,
        frequency: new Set(evidence.map((e) => e.docId)).size,
        mentions: evidence.length,
      },
    ];
  });
}

export function parseFeedback(value) {
  try {
    return typeof value === "string" ? JSON.parse(value) : value || {};
  } catch {
    return {};
  }
}

export function safeSource(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" && parsed.hostname === "www.yuque.com"
      ? parsed.href
      : "";
  } catch {
    return "";
  }
}
