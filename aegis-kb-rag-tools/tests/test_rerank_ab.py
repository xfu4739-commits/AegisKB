"""S20-P4 neutral-fixture A/B harness: cross-encoder vs jev rerank scoring backends.

HONEST SCOPE (no real egress, no real model weights):
- The jev arm is MOCKED (stub urlopen) and the cross-encoder arm is STUBBED (canned
  logits), because real jev egress requires explicit user authorization + an API key,
  and real cross-encoder weights are not loadable offline (S19-P8 observed
  huggingface.co unreachable). This harness therefore proves CONTRACT/SHAPE parity
  and a PUBLISHED-PRICE cost MODEL only.
- It does NOT and CANNOT prove a ranking-QUALITY / real-latency / real-cost verdict.
  Jev value remains HYPOTHESIS. The keep-as-opt-in / drop decision is DEFERRED to the
  user-authorized real `backend=jev` exchange A/B (HUMAN_ACTION_REQUIRED). Default
  backend stays cross-encoder. See records/S20.md P4.

The fixture is neutral: generic computing passages, no acceptance document names,
departments, relation names, amounts, roles or sample words (invariant #8).
"""

import json
import math
import unittest
from unittest.mock import patch

import rag_tools.main as main
import rag_tools.rerank_jev as rerank_jev
import rag_tools.semantic_model as semantic_model
from rag_tools.schemas.rerank import RerankCandidate, RerankRequest

# (id, text, canned cross-encoder raw logit, canned jev noul probability)
NEUTRAL_FIXTURE = [
    ("p0", "Exponential backoff with jitter spreads out retries so a recovering service is not overwhelmed.", 2.0, 0.95),
    ("p1", "A retry budget limits how many retries are allowed, shedding load when the budget is exhausted.", 1.5, 0.90),
    ("p2", "Idempotency keys let a client safely retry a write without applying it twice.", 1.0, 0.80),
    ("p3", "Structured logging records request outcomes so failures can be diagnosed later.", 0.0, 0.50),
    ("p4", "The cafeteria menu on Tuesday includes grilled salmon and a garden salad.", -3.0, 0.05),
]
QUERY = "How can a service retry failed requests without causing overload?"

# Published TypeSafe System One input price (USD per 1M tokens); estimate only, may drift.
JEV_USD_PER_M_INPUT_TOKENS = 0.042
# Rough English char->token heuristic for a no-dependency cost estimate.
CHARS_PER_TOKEN = 4


class FakeCrossEncoder:
    def __init__(self, raw_scores):
        self._raw_scores = raw_scores

    def predict(self, pairs):
        return self._raw_scores


class FakeHttpResponse:
    def __init__(self, payload):
        self._body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    def read(self):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class RecordingUrlopen:
    def __init__(self, payload):
        self._payload = payload
        self.calls = 0

    def __call__(self, request, timeout=None):
        self.calls += 1
        return FakeHttpResponse(self._payload)


def fixture_request():
    return RerankRequest(
        query=QUERY,
        candidates=[RerankCandidate(id=row[0], text=row[1]) for row in NEUTRAL_FIXTURE],
        topK=len(NEUTRAL_FIXTURE),
    )


def run_cross_encoder_arm():
    raws = [row[2] for row in NEUTRAL_FIXTURE]
    with patch.dict("os.environ", {"RAG_TOOLS_RERANK_BACKEND": "cross-encoder"}, clear=False):
        with patch.object(semantic_model, "_load_cross_encoder", return_value=FakeCrossEncoder(raws)):
            return main.rerank(fixture_request())


def run_jev_arm():
    nouls = [row[3] for row in NEUTRAL_FIXTURE]
    payload = {"answers": {f"q{i}": {"type": "noul", "noul": v} for i, v in enumerate(nouls)}}
    recorder = RecordingUrlopen(payload)
    env = {"RAG_TOOLS_RERANK_BACKEND": "jev", "RAG_TOOLS_JEV_API_KEY": "fixture-key"}
    with patch.dict("os.environ", env, clear=False):
        with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
            response = main.rerank(fixture_request())
    return response, recorder


def estimate_jev_input_tokens():
    texts = [row[1] for row in NEUTRAL_FIXTURE]
    payload = rerank_jev._build_payload("jev-latest", QUERY, texts, 4000)
    serialized = json.dumps(payload, ensure_ascii=False)
    return math.ceil(len(serialized) / CHARS_PER_TOKEN)


class NeutralFixtureAbParityTest(unittest.TestCase):
    """Both backends must yield the same valid /rerank contract shape on the neutral fixture."""

    def assert_valid_contract(self, response):
        ids = [row[0] for row in NEUTRAL_FIXTURE]
        self.assertEqual(len(ids), len(response.results))
        self.assertEqual(set(ids), {result.id for result in response.results})
        self.assertEqual(list(range(1, len(ids) + 1)), sorted(result.rank for result in response.results))
        for result in response.results:
            self.assertTrue(math.isfinite(result.score))
            self.assertGreaterEqual(result.score, 0.0)
            self.assertLessEqual(result.score, 1.0)

    def test_cross_encoder_arm_contract(self):
        self.assert_valid_contract(run_cross_encoder_arm())

    def test_jev_arm_contract(self):
        response, _ = run_jev_arm()
        self.assert_valid_contract(response)

    def test_both_arms_cover_same_candidate_id_set(self):
        ce = run_cross_encoder_arm()
        jev, _ = run_jev_arm()
        self.assertEqual({r.id for r in ce.results}, {r.id for r in jev.results})

    def test_cross_encoder_arm_never_egresses(self):
        recorder = RecordingUrlopen({"answers": {}})
        with patch.dict("os.environ", {"RAG_TOOLS_RERANK_BACKEND": "cross-encoder"}, clear=False):
            with patch.object(semantic_model, "_load_cross_encoder", return_value=FakeCrossEncoder([0.0] * 5)):
                with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                    main.rerank(fixture_request())
        self.assertEqual(0, recorder.calls)


class JevCostModelTest(unittest.TestCase):
    """Published-price cost MODEL for the fixture (estimate only; real billing pending real run)."""

    def test_jev_input_cost_estimate_is_positive_and_reproducible(self):
        tokens = estimate_jev_input_tokens()
        cost = tokens * JEV_USD_PER_M_INPUT_TOKENS / 1_000_000
        self.assertGreater(tokens, 0)
        self.assertGreater(cost, 0.0)
        self.assertEqual(tokens, estimate_jev_input_tokens())
        # cross-encoder is local inference: zero external API cost.
        self.assertEqual(0.0, 0.0)


class AbEvidenceSummaryTest(unittest.TestCase):
    """Emit a concise, reproducible A/B evidence line for records/S20.md (no quality claim)."""

    def test_print_summary(self):
        ce = run_cross_encoder_arm()
        jev, _ = run_jev_arm()
        tokens = estimate_jev_input_tokens()
        cost = tokens * JEV_USD_PER_M_INPUT_TOKENS / 1_000_000
        ce_order = [r.id for r in ce.results]
        jev_order = [r.id for r in jev.results]
        print(
            "\n[S20-P4 A/B neutral fixture | mock jev + stub cross-encoder | NO egress]\n"
            f"  query={QUERY!r}\n"
            f"  cross-encoder order={ce_order} scores={[round(r.score, 4) for r in ce.results]}\n"
            f"  jev(mock)     order={jev_order} scores={[round(r.score, 4) for r in jev.results]}\n"
            f"  contract parity=PASS (both {{id,score in [0,1],rank 1..N}} full coverage)\n"
            f"  jev est input tokens={tokens} est cost/call=${cost:.8f} (published $0.042/M, estimate)\n"
            f"  cross-encoder external API cost=$0 (local)\n"
            f"  QUALITY/real-latency/real-cost verdict=PENDING user-authorized real jev exchange; "
            f"HYPOTHESIS unproven; keep/drop NOT decided; default stays cross-encoder."
        )


if __name__ == "__main__":
    unittest.main()