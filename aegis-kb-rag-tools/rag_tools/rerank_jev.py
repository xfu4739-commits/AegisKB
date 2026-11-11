import json
import logging
import math
import urllib.error
import urllib.request

from rag_tools.config import config_float, config_int, config_value
from rag_tools.semantic_model import SemanticModelUnavailable

logger = logging.getLogger(__name__)

DEFAULT_JEV_BASE_URL = "https://api.typesafe.ai"
DEFAULT_JEV_MODEL = "jev-latest"
DEFAULT_JEV_TIMEOUT_SECONDS = 30.0
DEFAULT_JEV_MAX_PASSAGE_CHARS = 4000

_RELEVANCE_CRITERIA = {
    "true": "The passage contains information that helps answer the query in the state.",
    "false": "The passage does not contain information that helps answer the query in the state.",
}


def score_rerank_pairs_jev(query: str, texts: list[str]) -> list[float]:
    if not texts:
        return []
    base_url = config_value("ragTools.rerank.jev.baseUrl", "RAG_TOOLS_JEV_BASE_URL", DEFAULT_JEV_BASE_URL)
    api_key = config_value("ragTools.rerank.jev.apiKey", "RAG_TOOLS_JEV_API_KEY", "")
    model = config_value("ragTools.rerank.jev.model", "RAG_TOOLS_JEV_MODEL", DEFAULT_JEV_MODEL)
    timeout = config_float(
        "ragTools.rerank.jev.timeoutSeconds", "RAG_TOOLS_JEV_TIMEOUT_SECONDS", DEFAULT_JEV_TIMEOUT_SECONDS
    )
    max_passage_chars = config_int(
        "ragTools.rerank.jev.maxPassageChars", "RAG_TOOLS_JEV_MAX_PASSAGE_CHARS", DEFAULT_JEV_MAX_PASSAGE_CHARS, min_value=1
    )
    if not api_key:
        # 出境门控：没有 key 绝不把私有 KB 文本发往外部 API，fail-closed 交给 Java 降级 weighted-hybrid。
        raise SemanticModelUnavailable("Jev 重排后端未配置 API key（RAG_TOOLS_JEV_API_KEY），已拒绝出境并降级。")
    endpoint = _jev_endpoint(base_url)
    payload = _build_payload(model, query or "", texts, max_passage_chars)
    parsed = _post_json(endpoint, api_key, payload, timeout, model)
    return _parse_scores(parsed, len(texts))


def _jev_endpoint(base_url: str) -> str:
    normalized = (base_url or "").strip().rstrip("/")
    if normalized.endswith("/v1/systemone"):
        return normalized
    return normalized + "/v1/systemone"


def _build_payload(model: str, query: str, texts: list[str], max_passage_chars: int) -> dict[str, object]:
    questions: dict[str, object] = {}
    for index, text in enumerate(texts):
        passage = (text or "")[:max_passage_chars]
        questions[f"q{index}"] = {
            "type": "noul",
            "instructions": (
                "The state contains a search query. Judge whether the passage below is relevant to that "
                "query, i.e. whether it contains information that helps answer it.\n\nPassage:\n" + passage
            ),
            "criteria": dict(_RELEVANCE_CRITERIA),
        }
    return {"model": model, "state": query, "questions": questions}


def _post_json(endpoint: str, api_key: str, payload: dict[str, object], timeout: float, model: str) -> object:
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
        return json.loads(body)
    except Exception as exception:
        logger.warning("Jev rerank request failed: endpoint=%s model=%s error=%s", endpoint, model, exception)
        raise SemanticModelUnavailable(f"Jev 重排请求失败: {exception}") from exception


def _parse_scores(parsed: object, expected_count: int) -> list[float]:
    answers = parsed.get("answers") if isinstance(parsed, dict) else None
    if not isinstance(answers, dict):
        raise SemanticModelUnavailable("Jev 响应缺少 answers 对象，已降级。")
    scores: list[float] = []
    for index in range(expected_count):
        key = f"q{index}"
        answer = answers.get(key)
        if not isinstance(answer, dict):
            raise SemanticModelUnavailable(f"Jev 响应缺少候选 {key} 的判定，已降级。")
        noul = answer.get("noul")
        if isinstance(noul, bool) or not isinstance(noul, (int, float)):
            raise SemanticModelUnavailable(f"Jev 候选 {key} 的 noul 不是数值，已降级。")
        value = float(noul)
        if not math.isfinite(value) or value < 0.0 or value > 1.0:
            raise SemanticModelUnavailable(f"Jev 候选 {key} 的 noul 超出 [0,1] 或非有限值，已降级。")
        scores.append(value)
    return scores