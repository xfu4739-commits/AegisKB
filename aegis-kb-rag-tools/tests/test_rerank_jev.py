import json
import math
import unittest
import urllib.error
from unittest.mock import patch

from fastapi import HTTPException

import rag_tools.main as main
import rag_tools.rerank_jev as rerank_jev
import rag_tools.semantic_model as semantic_model
from rag_tools.schemas.rerank import RerankCandidate, RerankRequest
from rag_tools.semantic_model import SemanticModelUnavailable

JEV_ENV = {"RAG_TOOLS_RERANK_BACKEND": "jev", "RAG_TOOLS_JEV_API_KEY": "fixture-key"}


class FakeHttpResponse:
    def __init__(self, body_bytes):
        self._body = body_bytes

    def read(self):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class RecordingUrlopen:
    """Stub urlopen: records the Request, returns a canned body or raises. No real egress."""

    def __init__(self, payload=None, error=None, raw_body=None):
        self._payload = payload
        self._error = error
        self._raw_body = raw_body
        self.request = None
        self.timeout = None
        self.calls = 0

    def __call__(self, request, timeout=None):
        self.calls += 1
        self.request = request
        self.timeout = timeout
        if self._error is not None:
            raise self._error
        body = (
            self._raw_body
            if self._raw_body is not None
            else json.dumps(self._payload, ensure_ascii=False).encode("utf-8")
        )
        return FakeHttpResponse(body)

    def body_json(self):
        return json.loads(self.request.data.decode("utf-8"))


class FakeCrossEncoder:
    def __init__(self, raw_scores):
        self._raw_scores = raw_scores

    def predict(self, pairs):
        return self._raw_scores


def answers_payload(nouls):
    return {
        "model": "jev-latest",
        "answers": {f"q{i}": {"type": "noul", "noul": value} for i, value in enumerate(nouls)},
        "usage": {},
    }


def request(candidate_ids, top_k, query="fixture query"):
    return RerankRequest(
        query=query,
        candidates=[RerankCandidate(id=cid, text="fixture text " + cid) for cid in candidate_ids],
        topK=top_k,
    )


class JevRequestContractTest(unittest.TestCase):
    """S20-P2: lock the Jev System One request shape against the official contract (mock, no egress)."""

    def test_posts_to_systemone_with_bearer_json_and_noul_questions(self):
        recorder = RecordingUrlopen(answers_payload([0.9, 0.2]))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                scores = rerank_jev.score_rerank_pairs_jev("fixture query", ["t0", "t1"])

        self.assertEqual([0.9, 0.2], scores)
        self.assertEqual(1, recorder.calls)
        self.assertEqual("https://api.typesafe.ai/v1/systemone", recorder.request.full_url)
        self.assertEqual("POST", recorder.request.get_method())
        self.assertEqual("Bearer fixture-key", recorder.request.get_header("Authorization"))
        self.assertEqual("application/json", recorder.request.get_header("Content-type"))
        body = recorder.body_json()
        self.assertEqual("jev-latest", body["model"])
        self.assertEqual("fixture query", body["state"])
        self.assertEqual({"q0", "q1"}, set(body["questions"]))
        self.assertEqual("noul", body["questions"]["q0"]["type"])
        self.assertIn("t0", body["questions"]["q0"]["instructions"])
        self.assertIn("t1", body["questions"]["q1"]["instructions"])
        self.assertEqual(rerank_jev._RELEVANCE_CRITERIA, body["questions"]["q0"]["criteria"])

    def test_uses_configured_base_url_model_and_timeout(self):
        recorder = RecordingUrlopen(answers_payload([0.5]))
        env = dict(JEV_ENV)
        env.update(
            {
                "RAG_TOOLS_JEV_BASE_URL": "https://example.test/",
                "RAG_TOOLS_JEV_MODEL": "jev-1.14.0",
                "RAG_TOOLS_JEV_TIMEOUT_SECONDS": "12.5",
            }
        )
        with patch.dict("os.environ", env, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

        self.assertEqual("https://example.test/v1/systemone", recorder.request.full_url)
        self.assertEqual("jev-1.14.0", recorder.body_json()["model"])
        self.assertEqual(12.5, recorder.timeout)

    def test_full_endpoint_base_url_not_doubled(self):
        recorder = RecordingUrlopen(answers_payload([0.5]))
        env = dict(JEV_ENV)
        env["RAG_TOOLS_JEV_BASE_URL"] = "https://api.typesafe.ai/v1/systemone"
        with patch.dict("os.environ", env, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

        self.assertEqual("https://api.typesafe.ai/v1/systemone", recorder.request.full_url)

    def test_passage_truncated_to_max_chars(self):
        recorder = RecordingUrlopen(answers_payload([0.5]))
        env = dict(JEV_ENV)
        env["RAG_TOOLS_JEV_MAX_PASSAGE_CHARS"] = "10"
        with patch.dict("os.environ", env, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                rerank_jev.score_rerank_pairs_jev("fixture query", ["x" * 50])

        instructions = recorder.body_json()["questions"]["q0"]["instructions"]
        self.assertIn("x" * 10, instructions)
        self.assertNotIn("x" * 11, instructions)


class JevResponseContractTest(unittest.TestCase):
    """S20-P2: Noul probability is the [0,1] score; full coverage required; invalid responses fail closed."""

    def test_noul_probability_maps_directly_to_unit_interval_score(self):
        nouls = [0.0, 0.5, 1.0, 0.999999]
        recorder = RecordingUrlopen(answers_payload(nouls))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                scores = rerank_jev.score_rerank_pairs_jev("fixture query", ["t"] * len(nouls))

        self.assertEqual(nouls, scores)
        for score in scores:
            self.assertTrue(math.isfinite(score))
            self.assertGreaterEqual(score, 0.0)
            self.assertLessEqual(score, 1.0)

    def test_missing_candidate_answer_fails_closed(self):
        recorder = RecordingUrlopen(answers_payload([0.9]))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0", "t1"])

    def test_missing_answers_object_fails_closed(self):
        recorder = RecordingUrlopen({"model": "jev-latest"})
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_non_numeric_noul_fails_closed(self):
        recorder = RecordingUrlopen({"answers": {"q0": {"type": "noul", "noul": "0.9"}}})
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_boolean_noul_fails_closed(self):
        recorder = RecordingUrlopen({"answers": {"q0": {"type": "noul", "noul": True}}})
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_out_of_range_noul_fails_closed(self):
        for bad in (1.5, -0.1):
            recorder = RecordingUrlopen(answers_payload([bad]))
            with patch.dict("os.environ", JEV_ENV, clear=False):
                with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                    with self.assertRaises(SemanticModelUnavailable):
                        rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_non_finite_noul_fails_closed(self):
        for bad in (float("nan"), float("inf")):
            recorder = RecordingUrlopen(answers_payload([bad]))
            with patch.dict("os.environ", JEV_ENV, clear=False):
                with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                    with self.assertRaises(SemanticModelUnavailable):
                        rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_invalid_json_body_fails_closed(self):
        recorder = RecordingUrlopen(raw_body=b"{not valid json")
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])


class JevEgressGateTest(unittest.TestCase):
    """S20-P2: no key => no private text egress; empty window => no egress."""

    def test_missing_api_key_blocks_egress_and_fails_closed(self):
        recorder = RecordingUrlopen(answers_payload([0.9]))
        env = {"RAG_TOOLS_RERANK_BACKEND": "jev", "RAG_TOOLS_JEV_API_KEY": ""}
        with patch.dict("os.environ", env, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["private kb text"])

        self.assertEqual(0, recorder.calls)

    def test_empty_texts_returns_empty_without_egress(self):
        recorder = RecordingUrlopen(answers_payload([]))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                scores = rerank_jev.score_rerank_pairs_jev("fixture query", [])

        self.assertEqual([], scores)
        self.assertEqual(0, recorder.calls)


class JevTransportFailureTest(unittest.TestCase):
    """S20-P2: transport/HTTP/timeout errors fail closed to SemanticModelUnavailable."""

    def test_http_error_fails_closed(self):
        error = urllib.error.HTTPError("https://api.typesafe.ai/v1/systemone", 401, "Unauthorized", {}, None)
        recorder = RecordingUrlopen(error=error)
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_url_error_fails_closed(self):
        recorder = RecordingUrlopen(error=urllib.error.URLError("connection refused"))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])

    def test_timeout_fails_closed(self):
        recorder = RecordingUrlopen(error=TimeoutError("timed out"))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                with self.assertRaises(SemanticModelUnavailable):
                    rerank_jev.score_rerank_pairs_jev("fixture query", ["t0"])


class RerankBackendDispatchTest(unittest.TestCase):
    """S20-P2: default cross-encoder unchanged; jev opt-in; unknown backend fail closed."""

    def test_default_backend_uses_cross_encoder_and_never_calls_jev(self):
        recorder = RecordingUrlopen(answers_payload([0.9]))
        with patch.dict("os.environ", {}, clear=True):
            with patch.object(semantic_model, "_load_cross_encoder", return_value=FakeCrossEncoder([0.5])):
                with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                    scores = semantic_model.score_rerank_pairs("fixture query", ["t0"])

        self.assertEqual([0.5], scores)
        self.assertEqual(0, recorder.calls)

    def test_blank_backend_defaults_to_cross_encoder(self):
        with patch.dict("os.environ", {"RAG_TOOLS_RERANK_BACKEND": "   "}, clear=False):
            with patch.object(semantic_model, "_load_cross_encoder", return_value=FakeCrossEncoder([0.3])):
                scores = semantic_model.score_rerank_pairs("fixture query", ["t0"])

        self.assertEqual([0.3], scores)

    def test_jev_backend_dispatches_to_adapter_not_cross_encoder(self):
        recorder = RecordingUrlopen(answers_payload([0.7]))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(semantic_model, "_load_cross_encoder") as loader:
                with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                    scores = semantic_model.score_rerank_pairs("fixture query", ["t0"])

        self.assertEqual([0.7], scores)
        loader.assert_not_called()
        self.assertEqual(1, recorder.calls)

    def test_unknown_backend_fails_closed(self):
        with patch.dict("os.environ", {"RAG_TOOLS_RERANK_BACKEND": "bogus"}, clear=False):
            with self.assertRaises(SemanticModelUnavailable):
                semantic_model.score_rerank_pairs("fixture query", ["t0"])

    def test_status_reports_active_backend(self):
        with patch.dict("os.environ", JEV_ENV, clear=False):
            self.assertEqual("jev", semantic_model.semantic_model_status()["rerankBackend"])
        with patch.dict("os.environ", {}, clear=True):
            self.assertEqual("cross-encoder", semantic_model.semantic_model_status()["rerankBackend"])


class JevRerankEndpointShapeTest(unittest.TestCase):
    """S20-P2: through the unchanged /rerank, Jev yields the same {id,score,rank} contract shape."""

    def test_jev_backend_produces_same_sort_rank_shape(self):
        recorder = RecordingUrlopen(answers_payload([0.2, 0.9, 0.5]))
        with patch.dict("os.environ", JEV_ENV, clear=False):
            with patch.object(rerank_jev.urllib.request, "urlopen", recorder):
                response = main.rerank(request(["c0", "c1", "c2"], top_k=3))

        self.assertEqual(["c1", "c2", "c0"], [result.id for result in response.results])
        self.assertEqual([1, 2, 3], [result.rank for result in response.results])
        self.assertEqual([0.9, 0.5, 0.2], [result.score for result in response.results])

    def test_jev_unavailable_maps_to_http_503_for_java_fallback(self):
        env = {"RAG_TOOLS_RERANK_BACKEND": "jev", "RAG_TOOLS_JEV_API_KEY": ""}
        with patch.dict("os.environ", env, clear=False):
            with self.assertRaises(HTTPException) as ctx:
                main.rerank(request(["c0"], top_k=1))

        self.assertEqual(503, ctx.exception.status_code)


if __name__ == "__main__":
    unittest.main()