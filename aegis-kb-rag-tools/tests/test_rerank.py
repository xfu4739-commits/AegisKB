import math
import unittest
from unittest.mock import patch

from fastapi import HTTPException

import rag_tools.main as main
import rag_tools.semantic_model as semantic_model
from rag_tools.schemas.rerank import RerankCandidate, RerankRequest
from rag_tools.semantic_model import SemanticModelUnavailable


class FakeCrossEncoder:
    """Stub cross-encoder: returns canned raw scores, records the pairs it received."""

    def __init__(self, raw_scores):
        self._raw_scores = raw_scores
        self.received_pairs = None

    def predict(self, pairs):
        self.received_pairs = pairs
        return self._raw_scores


def request(candidate_ids, top_k, query="fixture query"):
    return RerankRequest(
        query=query,
        candidates=[RerankCandidate(id=cid, text="fixture text " + cid) for cid in candidate_ids],
        topK=top_k,
    )


class RerankEndpointCharacterizationTest(unittest.TestCase):
    """S20-P1: freeze /rerank sorting, rank, top_k clamp and 503 degradation."""

    def test_sorts_by_score_desc_and_assigns_one_based_rank(self):
        with patch.object(main, "score_rerank_pairs", return_value=[0.2, 0.9, 0.5]) as scorer:
            response = main.rerank(request(["c0", "c1", "c2"], top_k=3))

        scorer.assert_called_once()
        self.assertEqual(["c1", "c2", "c0"], [result.id for result in response.results])
        self.assertEqual([1, 2, 3], [result.rank for result in response.results])
        self.assertEqual([0.9, 0.5, 0.2], [result.score for result in response.results])

    def test_ties_break_by_original_candidate_index(self):
        with patch.object(main, "score_rerank_pairs", return_value=[0.5, 0.5, 0.5]):
            response = main.rerank(request(["c0", "c1", "c2"], top_k=3))

        self.assertEqual(["c0", "c1", "c2"], [result.id for result in response.results])
        self.assertEqual([1, 2, 3], [result.rank for result in response.results])

    def test_top_k_zero_returns_empty_without_scoring(self):
        with patch.object(main, "score_rerank_pairs") as scorer:
            response = main.rerank(request(["c0", "c1"], top_k=0))

        scorer.assert_not_called()
        self.assertEqual([], response.results)

    def test_empty_candidates_returns_empty_without_scoring(self):
        with patch.object(main, "score_rerank_pairs") as scorer:
            response = main.rerank(request([], top_k=5))

        scorer.assert_not_called()
        self.assertEqual([], response.results)

    def test_top_k_clamped_to_candidate_count(self):
        with patch.object(main, "score_rerank_pairs", return_value=[0.9, 0.1]):
            response = main.rerank(request(["c0", "c1"], top_k=10))

        self.assertEqual(2, len(response.results))
        self.assertEqual(["c0", "c1"], [result.id for result in response.results])

    def test_top_k_truncates_to_highest_scores(self):
        with patch.object(main, "score_rerank_pairs", return_value=[0.2, 0.9, 0.5]):
            response = main.rerank(request(["c0", "c1", "c2"], top_k=2))

        self.assertEqual(["c1", "c2"], [result.id for result in response.results])
        self.assertEqual([1, 2], [result.rank for result in response.results])

    def test_score_rounded_to_six_decimals(self):
        with patch.object(main, "score_rerank_pairs", return_value=[0.1234567891]):
            response = main.rerank(request(["c0"], top_k=1))

        self.assertEqual(0.123457, response.results[0].score)

    def test_semantic_unavailable_maps_to_http_503(self):
        with patch.object(main, "score_rerank_pairs", side_effect=SemanticModelUnavailable("model down")):
            with self.assertRaises(HTTPException) as ctx:
                main.rerank(request(["c0"], top_k=1))

        self.assertEqual(503, ctx.exception.status_code)
        self.assertIn("model down", ctx.exception.detail)


class ScoreRerankPairsCharacterizationTest(unittest.TestCase):
    """S20-P1: freeze cross-encoder pair assembly and score normalization."""

    def test_empty_texts_returns_empty_without_loading_model(self):
        with patch.object(semantic_model, "_load_cross_encoder") as loader:
            scores = semantic_model.score_rerank_pairs("fixture query", [])

        loader.assert_not_called()
        self.assertEqual([], scores)

    def test_builds_query_text_pairs_and_coerces_none_to_empty(self):
        fake = FakeCrossEncoder([0.5, 0.5])
        with patch.object(semantic_model, "_load_cross_encoder", return_value=fake):
            semantic_model.score_rerank_pairs("fixture query", ["t1", None])

        self.assertEqual([("fixture query", "t1"), ("fixture query", "")], fake.received_pairs)

    def test_normalizes_raw_logits_to_unit_interval(self):
        raw = [0.0, 0.5, 1.0, 2.0, 100.0, -100.0, -2.0]
        fake = FakeCrossEncoder(raw)
        with patch.object(semantic_model, "_load_cross_encoder", return_value=fake):
            scores = semantic_model.score_rerank_pairs("fixture query", ["t"] * len(raw))

        expected = [0.0, 0.5, 1.0, 1.0 / (1.0 + math.exp(-2.0)), 1.0, 0.0, 1.0 / (1.0 + math.exp(2.0))]
        self.assertEqual(len(expected), len(scores))
        for actual, want in zip(scores, expected):
            self.assertAlmostEqual(want, actual, places=6)
            self.assertGreaterEqual(actual, 0.0)
            self.assertLessEqual(actual, 1.0)

    def test_model_name_read_from_env(self):
        fake = FakeCrossEncoder([0.5])
        with patch.dict("os.environ", {"RAG_TOOLS_RERANK_MODEL": "fixture-rerank-model"}, clear=False):
            with patch.object(semantic_model, "_load_cross_encoder", return_value=fake) as loader:
                semantic_model.score_rerank_pairs("fixture query", ["t"])

        loader.assert_called_once_with("fixture-rerank-model")

    def test_default_model_name_when_env_absent(self):
        fake = FakeCrossEncoder([0.5])
        with patch.dict("os.environ", {}, clear=True):
            with patch.object(semantic_model, "_load_cross_encoder", return_value=fake) as loader:
                semantic_model.score_rerank_pairs("fixture query", ["t"])

        loader.assert_called_once_with(semantic_model.DEFAULT_RERANK_MODEL)

    def test_model_unavailable_propagates(self):
        with patch.object(semantic_model, "_load_cross_encoder", side_effect=SemanticModelUnavailable("no weights")):
            with self.assertRaises(SemanticModelUnavailable):
                semantic_model.score_rerank_pairs("fixture query", ["t"])

    def test_predict_failure_wrapped_as_unavailable(self):
        class BrokenCrossEncoder:
            def predict(self, pairs):
                raise RuntimeError("inference exploded")

        with patch.object(semantic_model, "_load_cross_encoder", return_value=BrokenCrossEncoder()):
            with self.assertRaises(SemanticModelUnavailable):
                semantic_model.score_rerank_pairs("fixture query", ["t"])


if __name__ == "__main__":
    unittest.main()