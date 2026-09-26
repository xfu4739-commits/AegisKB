import re
import unittest
from pathlib import Path
from unittest.mock import patch

import rag_tools.graph_extract as graph_extract
from pydantic import ValidationError
from rag_tools.graph_extract import extract_graph
from rag_tools.schemas.graph_extract import GraphChunk, GraphExtractRequest, GraphRelation


class GraphExtractTest(unittest.TestCase):
    def setUp(self) -> None:
        self._env_patch = patch.dict(
            "os.environ",
            {"SUPER_AGENT_GRAPH_RAG_NER_MODEL_ENABLED": "false"},
            clear=False,
        )
        self._env_patch.start()

    def tearDown(self) -> None:
        self._env_patch.stop()
        graph_extract._SPACY_NLP = None
        graph_extract._SPACY_LOAD_ATTEMPTED = False
        graph_extract._SPACY_MODEL_NAME = ""
        graph_extract._NER_MODEL_PIPELINE = None
        graph_extract._NER_MODEL_LOAD_ATTEMPTED = False
        graph_extract._NER_MODEL_STATUS = None
        graph_extract._NER_MODEL_CONFIG_KEY = None

    def test_unfamiliar_domain_aliases_merge_without_rule_relations(self) -> None:
        request = GraphExtractRequest(
            documentId=710,
            taskId=720,
            chunks=[
                GraphChunk(
                    chunkId=7101,
                    chunkNo=1,
                    title="极光分光镜",
                    sectionPath="外行星观测/极光分光镜",
                    text=(
                        "极光分光镜（AuroraSpectrograph）测量 VelaRidge 的偏振谱。"
                        "这台仪器的校准样本来自 Cryolith-7。"
                    ),
                    contentWithWeight=(
                        "极光分光镜（AuroraSpectrograph）测量 VelaRidge 的偏振谱。"
                        "这台仪器的校准样本来自 Cryolith-7。"
                    ),
                ),
                GraphChunk(
                    chunkId=7102,
                    chunkNo=2,
                    title="偏振复核",
                    sectionPath="外行星观测/偏振复核",
                    text="AuroraSpectrograph（AS）复核 Cryolith-7 的第二组谱线。",
                    contentWithWeight="AuroraSpectrograph（AS）复核 Cryolith-7 的第二组谱线。",
                ),
            ],
        )

        response = extract_graph(request)

        instrument = self._entity_by_alias(response.entities, "极光分光镜")
        self.assertEqual("AuroraSpectrograph", instrument.name)
        self.assertIn("AS", instrument.aliases)
        self.assertEqual([7101, 7102], sorted(instrument.source_chunk_ids))
        self.assertFalse(response.relations)
        self.assertFalse(response.communities)
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)
        self.assert_structured_status(response, relation_status="empty")

    def test_weighted_metadata_is_not_treated_as_source_content(self) -> None:
        weighted = (
            "[TITLE] LumenWeave catalog "
            "[SECTION] Archive/LumenWeave "
            "[CHUNK_TYPE] TEXT "
            "[KEYWORDS] LumenWeave; Zephyria; false metadata entity "
            "[QUESTIONS] Which object belongs to LumenWeave? "
            "[CONTENT] section: LumenWeave catalogs the Zephyria shard and the Nacre-12 sample."
        )
        request = GraphExtractRequest(
            documentId=711,
            taskId=721,
            chunks=[
                GraphChunk(
                    chunkId=7111,
                    chunkNo=1,
                    title="LumenWeave catalog",
                    sectionPath="Archive/LumenWeave",
                    text=weighted,
                    contentWithWeight=weighted,
                )
            ],
        )

        response = extract_graph(request)

        entity_names = {entity.name for entity in response.entities}
        self.assertIn("LumenWeave", entity_names)
        self.assertTrue(
            {"TITLE", "SECTION", "CONTENT", "KEYWORDS", "QUESTIONS", "CHUNK_TYPE", "TEXT"}.isdisjoint(entity_names)
        )
        self.assertFalse(any("Which object" in name or "false metadata" in name for name in entity_names))
        self.assertFalse(response.relations)
        self.assertTrue(
            all("[TITLE]" not in evidence.quote_text and "[QUESTIONS]" not in evidence.quote_text for evidence in response.evidences)
        )
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)

    def test_configurable_model_ner_supplies_domain_neutral_typed_candidates(self) -> None:
        class FakeNerPipeline:
            def __call__(self, text: str):
                return [
                    {"word": "Zephyria Basin", "entity_group": "LOC"},
                    {"word": "VantaCrystal", "entity_group": "MATERIAL"},
                ]

        request = GraphExtractRequest(
            documentId=712,
            taskId=722,
            chunks=[
                GraphChunk(
                    chunkId=7121,
                    chunkNo=1,
                    title="Zephyria survey",
                    sectionPath="Mineralogy/Zephyria survey",
                    text="Zephyria Basin contains a thin VantaCrystal seam beside the pale regolith.",
                    contentWithWeight="Zephyria Basin contains a thin VantaCrystal seam beside the pale regolith.",
                )
            ],
        )

        with patch.dict(
            "os.environ",
            {
                "SUPER_AGENT_GRAPH_RAG_NER_MODEL_ENABLED": "true",
                "SUPER_AGENT_GRAPH_RAG_NER_MODEL_NAME": "local-domain-neutral-ner",
            },
            clear=False,
        ), patch.object(graph_extract, "_load_ner_model_pipeline", return_value=FakeNerPipeline()):
            response = extract_graph(request)

        basin = next(entity for entity in response.entities if entity.name == "Zephyria Basin")
        crystal = next(entity for entity in response.entities if entity.name == "VantaCrystal")
        self.assertEqual("LOCATION", basin.type)
        self.assertEqual("CONCEPT", crystal.type)
        self.assertIn("ner.model.transformers.loc", basin.metadata.get("candidateSources"))
        self.assertIn("ner.model.transformers.material", crystal.metadata.get("candidateSources"))
        self.assertIn("ner", basin.metadata.get("extractorSources"))
        self.assertFalse(response.relations)

        model_layer = next(
            layer for layer in response.metadata.get("extractorLayers") if layer.get("name") == "ner.model"
        )
        self.assertEqual("loaded", model_layer.get("status"))
        self.assertEqual("local-domain-neutral-ner", model_layer.get("modelName"))
        self.assertGreaterEqual(
            response.metadata.get("candidateSourceCounts", {}).get("ner.model.transformers.loc", 0),
            1,
        )
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)

    def test_unavailable_model_is_structured_and_does_not_restore_policy_facts(self) -> None:
        text = (
            "| L4 | 高敏感信息 | 部门负责人 + 数据治理负责人 + 信息安全部三级审批 |\n"
            "`AuditTrail` 需记录以下行为：\n"
            "- 权限申请。\n"
            "客户数据原则上仅允许存放于公司批准的平台：\n"
            "- 加密文件库：`VaultDocs`"
        )
        request = GraphExtractRequest(
            documentId=713,
            taskId=723,
            chunks=[
                GraphChunk(
                    chunkId=7131,
                    chunkNo=1,
                    title="旧规则反例",
                    sectionPath="反例/旧规则反例",
                    text=text,
                    contentWithWeight=text,
                )
            ],
        )

        with patch.dict(
            "os.environ",
            {
                "SUPER_AGENT_GRAPH_RAG_NER_MODEL_ENABLED": "true",
                "SUPER_AGENT_GRAPH_RAG_NER_MODEL_NAME": "missing-local-model",
            },
            clear=False,
        ), patch.object(
            graph_extract,
            "_load_ner_model_pipeline",
            side_effect=OSError("model weights unavailable"),
        ):
            response = extract_graph(request)

        self.assertFalse(response.relations)
        self.assertFalse(response.communities)
        model_layer = next(
            layer for layer in response.metadata.get("extractorLayers") if layer.get("name") == "ner.model"
        )
        self.assertEqual("unavailable", model_layer.get("status"))
        self.assertIn("model weights unavailable", model_layer.get("reason", ""))
        status = response.metadata.get("extractionStatus", {})
        self.assertEqual("graph-extraction-status.v1", status.get("schemaVersion"))
        self.assertEqual("completed_with_degradation", status.get("status"))
        self.assertIn("NER_MODEL_UNAVAILABLE", status.get("reasonCodes", []))
        self.assertEqual("empty", status.get("relationStatus"))
        self.assertEqual("NO_GROUNDED_RELATION_CANDIDATE", status.get("relationReason"))
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)

    def test_cross_chunk_policy_shape_does_not_invent_a_relation_or_quote(self) -> None:
        request = GraphExtractRequest(
            documentId=714,
            taskId=724,
            chunks=[
                GraphChunk(
                    chunkId=7141,
                    chunkNo=1,
                    title="KeplerLedger observations",
                    sectionPath="Exoplanet survey/KeplerLedger observations",
                    text="`KeplerLedger` 需记录以下行为：",
                    contentWithWeight="`KeplerLedger` 需记录以下行为：",
                ),
                GraphChunk(
                    chunkId=7142,
                    chunkNo=2,
                    title="Observation list",
                    sectionPath="Exoplanet survey/KeplerLedger observations",
                    text="- 潮汐偏振。\n- 暗面闪烁。",
                    contentWithWeight="- 潮汐偏振。\n- 暗面闪烁。",
                ),
            ],
        )

        response = extract_graph(request)

        self.assertFalse(response.relations)
        self.assertFalse(response.communities)
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)
        self.assert_structured_status(response, relation_status="empty")

    def test_plain_action_words_are_not_a_relation_extractor(self) -> None:
        request = GraphExtractRequest(
            documentId=715,
            taskId=725,
            chunks=[
                GraphChunk(
                    chunkId=7151,
                    chunkNo=1,
                    title="Field notes",
                    sectionPath="Cryovolcanism/Field notes",
                    text="MiraObserver triggers HelixLatch. OrisonProbe stores NacreDust.",
                    contentWithWeight="MiraObserver triggers HelixLatch. OrisonProbe stores NacreDust.",
                )
            ],
        )

        response = extract_graph(request)

        self.assertFalse(response.relations)
        self.assertFalse(response.communities)
        self.assert_grounded_response(request, response)
        self.assert_no_rule_sources(response)

    def test_ungrounded_title_and_section_candidates_fail_closed(self) -> None:
        request = GraphExtractRequest(
            documentId=716,
            taskId=726,
            chunks=[
                GraphChunk(
                    chunkId=7161,
                    chunkNo=1,
                    title="PhantomHeading",
                    sectionPath="DormantArchive/PhantomHeading",
                    text="岩芯样本保持静止。",
                    contentWithWeight="岩芯样本保持静止。",
                )
            ],
        )

        response = extract_graph(request)

        self.assertFalse(response.entities)
        self.assertFalse(response.relations)
        status = response.metadata.get("extractionStatus", {})
        self.assertEqual("empty", status.get("status"))
        self.assertIn("NO_GROUNDED_ENTITY_CANDIDATE", status.get("reasonCodes", []))

    def test_empty_input_has_structured_empty_result(self) -> None:
        response = extract_graph(GraphExtractRequest(documentId=717, taskId=727, chunks=[]))

        self.assertFalse(response.entities)
        self.assertFalse(response.relations)
        self.assertFalse(response.evidences)
        self.assertFalse(response.communities)
        status = response.metadata.get("extractionStatus", {})
        self.assertEqual("graph-extraction-status.v1", status.get("schemaVersion"))
        self.assertEqual("empty", status.get("status"))
        self.assertIn("EMPTY_INPUT", status.get("reasonCodes", []))
        self.assertEqual("empty", status.get("relationStatus"))

    def test_python_main_path_contains_no_sample_relation_producers(self) -> None:
        source = Path(graph_extract.__file__).read_text(encoding="utf-8")
        forbidden_snippets = {
            "StructuredRelationCandidate",
            "_structured_relation_candidates",
            "_cross_chunk_structured_relation_candidates",
            "_structured_relations_from_policy_text",
            "_policy_actor_like",
            "_approval_targets",
            "_data_grade_aliases",
            "_role_ner_terms",
            "_build_communities",
            "_sort_relations",
            "relation_map",
            "rule.structuredRelation",
            "信息安全部",
            "数据治理负责人",
            "部门负责人",
        }
        for snippet in sorted(forbidden_snippets):
            self.assertNotIn(snippet, source)

    def test_forbidden_python_relation_shape_cannot_default_a_relation_type(self) -> None:
        with self.assertRaises(ValidationError):
            GraphRelation(id="R1", sourceEntityId="E1", targetEntityId="E2")

    def _entity_by_alias(self, entities, alias: str):
        for entity in entities:
            if alias == entity.name or alias in entity.aliases:
                return entity
        self.fail(f"Missing entity or alias: {alias}")

    def assert_grounded_response(self, request: GraphExtractRequest, response) -> None:
        chunks = {chunk.chunk_id: chunk for chunk in request.chunks if chunk.chunk_id is not None}
        evidence_by_id = {evidence.id: evidence for evidence in response.evidences}

        for evidence in response.evidences:
            self.assertIn(evidence.chunk_id, chunks)
            source_text = graph_extract._chunk_evidence_text(chunks[evidence.chunk_id])
            self.assertIn(self._compact(evidence.quote_text), self._compact(source_text))
            self.assertTrue(evidence.entity_id or evidence.relation_id)
            self.assertTrue(evidence.metadata.get("grounded"))

        for entity in response.entities:
            self.assertTrue(entity.evidence_ids, f"entity must be grounded: {entity.name}")
            self.assertTrue(all(evidence_id in evidence_by_id for evidence_id in entity.evidence_ids))

        for relation in response.relations:
            self.assertTrue(relation.evidence_ids, f"relation must be grounded: {relation.id}")
            relation_evidence = [evidence_by_id[evidence_id] for evidence_id in relation.evidence_ids]
            self.assertTrue(all(evidence.relation_id == relation.id for evidence in relation_evidence))
            self.assertTrue(all(evidence.quote_text for evidence in relation_evidence))

    def assert_no_rule_sources(self, response) -> None:
        for item in [*response.entities, *response.relations, *response.evidences]:
            extractor_sources = set((item.metadata or {}).get("extractorSources") or [])
            candidate_sources = set((item.metadata or {}).get("candidateSources") or [])
            self.assertNotIn("rule", extractor_sources)
            self.assertFalse(any(source.startswith("rule.") for source in candidate_sources))

    def assert_structured_status(self, response, relation_status: str) -> None:
        status = response.metadata.get("extractionStatus", {})
        self.assertEqual("graph-extraction-status.v1", status.get("schemaVersion"))
        self.assertIn(status.get("status"), {"completed", "completed_with_degradation", "empty"})
        self.assertEqual(len(response.entities), status.get("entityCount"))
        self.assertEqual(len(response.relations), status.get("relationCount"))
        self.assertEqual(len(response.evidences), status.get("evidenceCount"))
        self.assertEqual(relation_status, status.get("relationStatus"))
        if relation_status == "empty":
            self.assertEqual("NO_GROUNDED_RELATION_CANDIDATE", status.get("relationReason"))

    @staticmethod
    def _compact(value: str) -> str:
        return re.sub(r"\s+", " ", value or "").strip()


if __name__ == "__main__":
    unittest.main()
