import hashlib
import os
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from rag_tools.schemas.graph_extract import (
    GraphEntity,
    GraphEvidence,
    GraphExtractRequest,
    GraphExtractResponse,
    GraphRelation,
)

MAX_ENTITIES_PER_CHUNK = 16
MAX_QUOTE_LENGTH = 260
EXTRACTOR_SCHEMA = "schema"
EXTRACTOR_NER = "ner"
EXTRACTION_STATUS_SCHEMA = "graph-extraction-status.v1"
NER_MODEL_ENV_ENABLED = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_ENABLED"
NER_MODEL_ENV_PROVIDER = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_PROVIDER"
NER_MODEL_ENV_NAME = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_NAME"
NER_MODEL_ENV_DEVICE = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_DEVICE"
NER_MODEL_ENV_AUTO_DOWNLOAD = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_AUTO_DOWNLOAD"
NER_MODEL_ENV_MAX_CHARS = "SUPER_AGENT_GRAPH_RAG_NER_MODEL_MAX_CHARS"
NER_MODEL_DEFAULT_PROVIDER = "transformers"
NER_MODEL_DEFAULT_MAX_CHARS = 8000

_SPACY_NLP = None
_SPACY_LOAD_ATTEMPTED = False
_SPACY_MODEL_NAME = ""
_NER_MODEL_PIPELINE = None
_NER_MODEL_LOAD_ATTEMPTED = False
_NER_MODEL_STATUS: dict[str, Any] | None = None
_NER_MODEL_CONFIG_KEY: tuple[Any, ...] | None = None


@dataclass
class CandidateName:
    name: str
    score: float = 0.0
    aliases: set[str] = field(default_factory=set)
    sources: set[str] = field(default_factory=set)
    entity_type: str = ""
    entity_type_priority: float = 0.0


def extract_graph(request: GraphExtractRequest) -> GraphExtractResponse:
    entity_map: dict[str, GraphEntity] = {}
    entity_alias_index: dict[str, str] = {}
    evidences: list[GraphEvidence] = []
    evidence_no = 1
    valid_chunk_count = 0

    for chunk in request.chunks:
        if chunk.chunk_id is None:
            continue
        text = _chunk_text(chunk)
        if not text:
            continue
        evidence_text = _chunk_evidence_text(chunk)
        if not evidence_text:
            continue
        valid_chunk_count += 1

        def upsert_entity_candidate(candidate: CandidateName) -> None:
            nonlocal evidence_no
            entity_type = _classify_entity(candidate, chunk)
            variants = _entity_variants(candidate.name, candidate.aliases)
            entity_id = _resolve_entity_id(variants, entity_alias_index)
            entity = entity_map.get(entity_id)
            if entity is None:
                entity = GraphEntity(
                    id=entity_id,
                    name=candidate.name,
                    normalizedName=_normalize_entity(candidate.name),
                    aliases=sorted(_valid_aliases(candidate.aliases, candidate.name)),
                    type=entity_type,
                    description=_entity_description(candidate.name, entity_type, candidate.aliases, chunk.section_path),
                    confidence=_entity_confidence(candidate.score),
                    metadata={
                        "firstChunkNo": chunk.chunk_no,
                        "firstSectionPath": chunk.section_path,
                        "mentionCount": 0,
                        "candidateScore": 0.0,
                        "candidateSources": [],
                        "entityTypePriority": candidate.entity_type_priority,
                    },
                )
                entity_map[entity_id] = entity
            _merge_entity_candidate(entity, candidate, entity_type, chunk)
            _index_entity_aliases(entity_id, _entity_variants(entity.name, set(entity.aliases)), entity_alias_index)
            if chunk.chunk_id not in entity.source_chunk_ids:
                entity.source_chunk_ids.append(chunk.chunk_id)
            evidence = _entity_evidence(f"EV{evidence_no}", entity_id, chunk, evidence_text, candidate)
            if evidence is None:
                raise ValueError(f"ungrounded entity candidate reached upsert: {candidate.name}")
            evidence_no += 1
            evidences.append(evidence)
            if evidence.id not in entity.evidence_ids:
                entity.evidence_ids.append(evidence.id)
        candidates = [
            candidate
            for candidate in _candidate_names(chunk, evidence_text)
            if _candidate_quote(evidence_text, candidate)
        ]
        for candidate in candidates[:MAX_ENTITIES_PER_CHUNK]:
            upsert_entity_candidate(candidate)

    entities = _sort_entities(entity_map.values())
    relations: list[GraphRelation] = []
    return GraphExtractResponse(
        entities=entities,
        relations=relations,
        evidences=evidences,
        communities=[],
        metadata=_extract_metadata(entities, relations, evidences, valid_chunk_count),
    )


def _chunk_text(chunk) -> str:
    return (chunk.content_with_weight or chunk.text or "").strip()


def _chunk_evidence_text(chunk) -> str:
    text = (chunk.text or "").strip()
    if text and not _metadata_weighted_text(text):
        return text
    weighted = (chunk.content_with_weight or "").strip()
    if not weighted:
        return text
    content = _content_segment_from_weighted_text(weighted)
    return content or text or weighted


def _metadata_weighted_text(text: str) -> bool:
    return bool(re.search(r"\[(?:TITLE|SECTION|CHUNK_TYPE|KEYWORDS|QUESTIONS|CONTENT|TEXT)\]", text or "", re.IGNORECASE))


def _content_segment_from_weighted_text(text: str) -> str:
    if not text:
        return ""
    match = re.search(r"\[CONTENT\]\s*(.*)$", text, re.IGNORECASE | re.DOTALL)
    if not match:
        return ""
    content = match.group(1)
    content = re.split(r"\[(?:TITLE|SECTION|CHUNK_TYPE|KEYWORDS|QUESTIONS|TEXT)\]", content, maxsplit=1, flags=re.IGNORECASE)[0]
    content = re.sub(r"^\s*section\s*:\s*", "", content.strip(), flags=re.IGNORECASE)
    return content.strip()


def _candidate_names(chunk, text: str) -> list[CandidateName]:
    candidates: dict[str, CandidateName] = {}

    def add(
        name: str,
        score: float,
        source: str,
        aliases: set[str] | None = None,
        entity_type: str = "",
        entity_type_priority: float = 0.0,
    ) -> None:
        cleaned = _clean_name(name)
        if not _valid_name(cleaned):
            return
        candidate = candidates.setdefault(cleaned, CandidateName(name=cleaned))
        candidate.score += score
        candidate.sources.add(source)
        _merge_candidate_type(candidate, entity_type, entity_type_priority)
        for alias in aliases or set():
            alias_cleaned = _clean_name(alias)
            if _valid_name(alias_cleaned) and _normalize_entity(alias_cleaned) != _normalize_entity(cleaned):
                candidate.aliases.add(alias_cleaned)

    for part in _split_section_path(chunk.section_path):
        add(part, 8, "schema.sectionPath", entity_type="SECTION", entity_type_priority=8)
    add(chunk.title, 10, "schema.title", entity_type="SECTION", entity_type_priority=10)

    for primary, alias in _alias_pairs(text + "\n" + (chunk.title or "")):
        add(primary, 10, "schema.explicitAlias", {alias})
        add(alias, 4, "schema.explicitAlias", {primary})

    for term in re.findall(r"\b(?:[A-Z][A-Za-z0-9]+(?:[\s/-]+|$)){2,5}", text):
        add(term, 5, "ner.pattern.englishPhrase")

    for term in re.findall(r"[\u4e00-\u9fff]{1,8}\s+[A-Z][A-Za-z0-9._/-]{1,16}|[A-Z][A-Za-z0-9._/-]{1,16}\s+[\u4e00-\u9fff]{1,8}", text):
        add(term, 8, "ner.pattern.mixedPhrase")

    for term in re.findall(r"\b[A-Za-z][A-Za-z0-9._/-]{1,40}\b", text):
        if any(char.isupper() for char in term) or re.search(r"[0-9._/-]", term):
            add(term, 4, "ner.pattern.englishToken")

    for candidate in _ner_candidate_names(text):
        cleaned = _clean_name(candidate.name)
        if not _valid_name(cleaned):
            continue
        merged = candidates.setdefault(cleaned, CandidateName(name=cleaned))
        merged.score += candidate.score
        merged.sources.update(candidate.sources or {EXTRACTOR_NER})
        _merge_candidate_type(merged, candidate.entity_type, candidate.entity_type_priority)
        for alias in candidate.aliases:
            alias_cleaned = _clean_name(alias)
            if _valid_name(alias_cleaned) and _normalize_entity(alias_cleaned) != _normalize_entity(cleaned):
                merged.aliases.add(alias_cleaned)

    for candidate in list(candidates.values()):
        acronym = _english_acronym(candidate.name)
        if acronym:
            candidate.aliases.add(acronym)

    return sorted(
        candidates.values(),
        key=lambda item: (-item.score, -len(item.aliases), -_candidate_name_priority(item), item.name.lower()),
    )


def _ner_candidate_names(text: str) -> list[CandidateName]:
    candidates: dict[str, CandidateName] = {}

    def add(name: str, score: float, source: str, entity_type: str = "", type_priority: float = 0.0) -> None:
        cleaned = _clean_name(name)
        if not _valid_name(cleaned):
            return
        candidate = candidates.setdefault(cleaned, CandidateName(name=cleaned))
        candidate.score += score
        candidate.sources.add(source)
        _merge_candidate_type(candidate, entity_type, type_priority)

    for name, source, entity_type in _spacy_ner_terms(text):
        add(name, 9, source, entity_type, 90)

    for name in _pattern_ner_terms(text):
        add(name, 7, "ner.pattern")

    for name, source, entity_type in _model_ner_terms(text):
        add(name, 10, source, entity_type, 100)

    return sorted(candidates.values(), key=lambda item: (-item.score, -len(item.name), item.name.lower()))


def _spacy_ner_terms(text: str) -> list[tuple[str, str, str]]:
    global _SPACY_NLP, _SPACY_LOAD_ATTEMPTED, _SPACY_MODEL_NAME
    if not text or not re.search(r"[A-Za-z]", text):
        return []
    if _SPACY_LOAD_ATTEMPTED and _SPACY_NLP is None:
        return []
    if _SPACY_NLP is not None:
        nlp = _SPACY_NLP
    else:
        _SPACY_LOAD_ATTEMPTED = True
        nlp = None
        try:
            import spacy
        except ImportError:
            return []
        for model_name in ("en_core_web_sm", "en_core_web_md"):
            try:
                nlp = spacy.load(model_name)
                _SPACY_NLP = nlp
                _SPACY_MODEL_NAME = model_name
                break
            except OSError:
                continue
    if nlp is None:
        return []
    terms: list[tuple[str, str, str]] = []
    for entity in nlp(text[:8000]).ents:
        if entity.label_ in {"ORDINAL", "CARDINAL", "DATE", "TIME", "MONEY", "PERCENT", "QUANTITY"}:
            continue
        terms.append((
            entity.text,
            f"ner.spacy.{entity.label_.lower()}",
            _ner_entity_type(entity.label_),
        ))
    return terms


def _model_ner_terms(text: str) -> list[tuple[str, str, str]]:
    pipeline = _ensure_ner_model_pipeline()
    if pipeline is None:
        return []
    max_chars = _ner_model_max_chars()
    try:
        raw_entities = pipeline((text or "")[:max_chars])
    except Exception as exception:
        _set_ner_model_status("unavailable", f"inference failed: {exception}", loaded=False)
        return []

    terms: list[tuple[str, str, str]] = []
    for item in raw_entities or []:
        if not isinstance(item, dict):
            continue
        name = _model_ner_entity_text(item)
        label = _model_ner_entity_label(item)
        if not name:
            continue
        source = f"ner.model.{_ner_model_provider()}"
        if label:
            source = f"{source}.{label}"
        terms.append((name, source, _ner_entity_type(label)))
    return terms[:80]


def _ner_entity_type(label: str) -> str:
    normalized = (label or "").strip().lower()
    if normalized in {"per", "person"}:
        return "PERSON"
    if normalized in {"org", "organization"}:
        return "ORG"
    if normalized in {"loc", "location", "gpe", "geo"}:
        return "LOCATION"
    if normalized in {"event"}:
        return "EVENT"
    if normalized in {"product", "system", "technology"}:
        return "SYSTEM"
    return "CONCEPT"


def _ensure_ner_model_pipeline():
    global _NER_MODEL_PIPELINE, _NER_MODEL_LOAD_ATTEMPTED, _NER_MODEL_STATUS, _NER_MODEL_CONFIG_KEY
    config_key = _ner_model_config_key()
    if _NER_MODEL_CONFIG_KEY != config_key:
        _NER_MODEL_PIPELINE = None
        _NER_MODEL_LOAD_ATTEMPTED = False
        _NER_MODEL_STATUS = None
        _NER_MODEL_CONFIG_KEY = config_key

    enabled = _env_bool(NER_MODEL_ENV_ENABLED, False)
    provider = _ner_model_provider()
    model_name = _ner_model_name()
    if not enabled:
        _set_ner_model_status("disabled", "", loaded=False)
        return None
    if provider != NER_MODEL_DEFAULT_PROVIDER:
        _set_ner_model_status("unavailable", f"unsupported provider: {provider}", loaded=False)
        return None
    if not model_name:
        _set_ner_model_status("unavailable", f"{NER_MODEL_ENV_NAME} is required when model NER is enabled", loaded=False)
        return None
    if _NER_MODEL_PIPELINE is not None:
        return _NER_MODEL_PIPELINE
    if _NER_MODEL_LOAD_ATTEMPTED:
        return None

    _NER_MODEL_LOAD_ATTEMPTED = True
    auto_download = _env_bool(NER_MODEL_ENV_AUTO_DOWNLOAD, False)
    try:
        _NER_MODEL_PIPELINE = _load_ner_model_pipeline(model_name, auto_download, _ner_model_device())
        _set_ner_model_status("loaded", "", loaded=True)
        return _NER_MODEL_PIPELINE
    except ImportError:
        _set_ner_model_status("unavailable", "transformers is not installed", loaded=False)
        return None
    except Exception as exception:
        _set_ner_model_status("unavailable", str(exception), loaded=False)
        return None


def _load_ner_model_pipeline(model_name: str, auto_download: bool, device: int):
    from transformers import AutoModelForTokenClassification, AutoTokenizer, pipeline

    tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=not auto_download)
    model = AutoModelForTokenClassification.from_pretrained(model_name, local_files_only=not auto_download)
    return pipeline(
        "token-classification",
        model=model,
        tokenizer=tokenizer,
        aggregation_strategy="simple",
        device=device,
    )


def _model_ner_entity_text(item: dict[str, Any]) -> str:
    for key in ("word", "text", "entity"):
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return _clean_model_ner_word(value)
    return ""


def _model_ner_entity_label(item: dict[str, Any]) -> str:
    value = item.get("entity_group") or item.get("entity")
    if not isinstance(value, str):
        return ""
    return re.sub(r"[^a-z0-9_]+", "_", value.lower()).strip("_")


def _clean_model_ner_word(value: str) -> str:
    cleaned = (value or "").replace("##", "")
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return _clean_name(cleaned)


def _ner_model_config_key() -> tuple[Any, ...]:
    return (
        _env_bool(NER_MODEL_ENV_ENABLED, False),
        _ner_model_provider(),
        _ner_model_name(),
        _ner_model_device(),
        _env_bool(NER_MODEL_ENV_AUTO_DOWNLOAD, False),
        _ner_model_max_chars(),
    )


def _ner_model_provider() -> str:
    return os.getenv(NER_MODEL_ENV_PROVIDER, NER_MODEL_DEFAULT_PROVIDER).strip().lower() or NER_MODEL_DEFAULT_PROVIDER


def _ner_model_name() -> str:
    return os.getenv(NER_MODEL_ENV_NAME, "").strip()


def _ner_model_device() -> int:
    raw_value = os.getenv(NER_MODEL_ENV_DEVICE, "-1").strip()
    try:
        return int(raw_value)
    except ValueError:
        return -1


def _ner_model_max_chars() -> int:
    raw_value = os.getenv(NER_MODEL_ENV_MAX_CHARS, str(NER_MODEL_DEFAULT_MAX_CHARS)).strip()
    try:
        return max(256, min(20000, int(raw_value)))
    except ValueError:
        return NER_MODEL_DEFAULT_MAX_CHARS


def _set_ner_model_status(status: str, reason: str = "", loaded: bool = False) -> None:
    global _NER_MODEL_STATUS
    _NER_MODEL_STATUS = {
        "name": "ner.model",
        "enabled": _env_bool(NER_MODEL_ENV_ENABLED, False),
        "status": status,
        "provider": _ner_model_provider(),
        "modelName": _ner_model_name(),
        "device": _ner_model_device(),
        "autoDownload": _env_bool(NER_MODEL_ENV_AUTO_DOWNLOAD, False),
        "loaded": loaded,
    }
    if reason:
        _NER_MODEL_STATUS["reason"] = _compact_reason(reason)


def _ner_model_layer_status() -> dict[str, Any]:
    _ensure_ner_model_pipeline()
    return dict(_NER_MODEL_STATUS or {
        "name": "ner.model",
        "enabled": False,
        "status": "disabled",
        "provider": _ner_model_provider(),
        "modelName": _ner_model_name(),
        "loaded": False,
    })


def _compact_reason(reason: str) -> str:
    return re.sub(r"\s+", " ", reason or "").strip()[:500]


def _env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _pattern_ner_terms(text: str) -> list[str]:
    terms: list[str] = []
    patterns = [
        r"`([A-Za-z][A-Za-z0-9._/-]{1,40})`",
        r"\b[A-Z][A-Za-z0-9]*(?:[A-Z][A-Za-z0-9]*){1,}\b",
        r"\b[A-Z]{2,}(?:[A-Z0-9._/-]{0,20})\b",
        r"\b[A-Za-z][A-Za-z0-9._/-]{1,24}(?:Service|Server|Client|Gateway|Engine|API|SDK|DB|SQL)\b",
    ]
    for pattern in patterns:
        for match in re.finditer(pattern, text or "", re.IGNORECASE):
            value = match.group(1) if match.lastindex else match.group(0)
            cleaned = _clean_name(value)
            if _valid_name(cleaned) and cleaned not in terms:
                terms.append(cleaned)
    return terms[:40]


def _merge_candidate_type(candidate: CandidateName, entity_type: str, priority: float) -> None:
    if not entity_type or priority < candidate.entity_type_priority:
        return
    candidate.entity_type = entity_type
    candidate.entity_type_priority = priority


def _candidate_name_priority(candidate: CandidateName) -> int:
    priority = len(candidate.name)
    if candidate.sources.intersection({"schema.title", "schema.sectionPath", "ner.pattern.mixedPhrase"}):
        priority += 8
    if _is_acronym(candidate.name):
        priority -= 4
    return priority


def _alias_pairs(text: str) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    parenthetical_pattern = re.compile(
        r"([\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9_.·/\-\s]{1,40})[（(]"
        r"([A-Za-z][A-Za-z0-9_.\-]{1,39}|[\u4e00-\u9fff]{2,40})[）)]"
    )
    for match in parenthetical_pattern.finditer(text or ""):
        primary = _clean_name(match.group(1))
        alias = _clean_name(match.group(2))
        if _valid_name(primary) and _valid_name(alias):
            pairs.append((primary, alias))
    explicit_patterns = [
        re.compile(
            r"`?([\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9_.·/\-\s]{1,40})`?"
            r"[\s,，、;；:：]*"
            r"(?:简称为|缩写为|英文名为|英文名称为|又称|也称|别名为|简称|缩写|别名)"
            r"[\s`\"']*"
            r"([A-Za-z][A-Za-z0-9_.\-]{1,39}|[\u4e00-\u9fffA-Za-z0-9_.·/\-\s]{2,40})"
            r"[`\"']*"
        ),
        re.compile(
            r"([A-Za-z][A-Za-z0-9_.\-]{1,39}|[\u4e00-\u9fffA-Za-z0-9_.·/\-\s]{2,40})"
            r"(?:是|为)"
            r"([\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9_.·/\-\s]{1,40})"
            r"(?:的简称|的缩写|的英文名|的英文名称|的别名)"
        ),
    ]
    for pattern in explicit_patterns:
        for match in pattern.finditer(text or ""):
            primary = _clean_name(match.group(1))
            alias = _clean_name(match.group(2))
            if _valid_name(primary) and _valid_name(alias):
                pairs.append((primary, alias))
    return pairs


def _split_section_path(section_path: str) -> list[str]:
    if not section_path:
        return []
    return [
        part.strip()
        for part in re.split(r"[/\\>|＞:：\n\r\t]+", section_path)
        if part and part.strip()
    ]


def _clean_name(name: str) -> str:
    if not name:
        return ""
    cleaned = re.sub(r"\s+", " ", name).strip(" \t\r\n,，。；;:：()（）[]【】{}<>《》\"'")
    cleaned = re.sub(r"[（(][^）)]{1,20}[）)]", "", cleaned).strip()
    cleaned = re.sub(r"^(第[一二三四五六七八九十百千万0-9]+[章节条款项、.．-]*)", "", cleaned).strip()
    cleaned = re.sub(r"^(一|二|三|四|五|六|七|八|九|十)[、.．-]+", "", cleaned).strip()
    return cleaned


def _valid_name(name: str) -> bool:
    if not name or len(name) < 2 or len(name) > 40:
        return False
    if _metadata_label_like(name):
        return False
    if _question_like_name(name):
        return False
    if any(marker in name for marker in ("简称", "又称", "也称", "别名", "英文名", "英文名称", "缩写")):
        return False
    if re.fullmatch(r"[0-9._/-]+", name):
        return False
    if re.fullmatch(r"[_\-*/#`~!@#$%^&+=]+", name):
        return False
    if re.fullmatch(r"[A-Za-z]{1,2}", name):
        return bool(re.fullmatch(r"[A-Z]{2}", name))
    if re.fullmatch(r"[A-Za-z]+", name) and name.islower():
        return False
    if len(re.findall(r"[\u4e00-\u9fffA-Za-z]", name)) < 2:
        return False
    return True


def _metadata_label_like(name: str) -> bool:
    return bool(re.fullmatch(
        r"\[?(?:title|section|chunk_type|keywords|questions|content|text|metadata|ct)\]?",
        name or "",
        re.IGNORECASE,
    ))


def _question_like_name(name: str) -> bool:
    return bool(re.search(r"[?？]$", name or ""))


def _classify_entity(candidate: CandidateName, chunk) -> str:
    if candidate.entity_type:
        return candidate.entity_type
    if candidate.name and candidate.name == chunk.title:
        return "SECTION"
    if any(part == candidate.name for part in _split_section_path(chunk.section_path)):
        return "SECTION"
    return "CONCEPT"


def _entity_description(name: str, entity_type: str, aliases: set[str], section_path: str) -> str:
    alias_text = ""
    if aliases:
        alias_text = "，别名：" + "、".join(sorted(aliases)[:5])
    if section_path:
        return f"{name}，类型：{entity_type}{alias_text}，来自章节：{section_path}"
    return f"{name}，类型：{entity_type}{alias_text}"


def _entity_confidence(score: float) -> float:
    return round(min(0.99, 0.35 + score / 30.0), 4)


def _entity_variants(name: str, aliases: set[str] | list[str]) -> list[str]:
    variants: list[str] = []
    for item in [name, *list(aliases or [])]:
        cleaned = _clean_name(item)
        if not cleaned:
            continue
        variants.append(cleaned)
        acronym = _english_acronym(cleaned)
        if acronym:
            variants.append(acronym)
    return _unique(variants)


def _resolve_entity_id(variants: list[str], alias_index: dict[str, str]) -> str:
    normalized_variants = [_normalize_entity(variant) for variant in variants if _normalize_entity(variant)]
    for variant in normalized_variants:
        entity_id = alias_index.get(_entity_alias_key(variant))
        if entity_id:
            return entity_id
    primary = normalized_variants[0] if normalized_variants else ""
    return "ENT_" + _hash(f"entity:{primary}")


def _index_entity_aliases(entity_id: str, variants: list[str], alias_index: dict[str, str]) -> None:
    for variant in variants:
        normalized = _normalize_entity(variant)
        if normalized:
            alias_index[_entity_alias_key(normalized)] = entity_id


def _entity_alias_key(normalized_variant: str) -> str:
    return normalized_variant


def _merge_entity_candidate(entity: GraphEntity, candidate: CandidateName, entity_type: str, chunk) -> None:
    metadata = entity.metadata or {}
    metadata["mentionCount"] = int(metadata.get("mentionCount") or 0) + 1
    metadata["candidateScore"] = round(float(metadata.get("candidateScore") or 0.0) + candidate.score, 4)
    metadata["lastChunkNo"] = chunk.chunk_no
    metadata["lastSectionPath"] = chunk.section_path
    sources = set(metadata.get("candidateSources") or [])
    sources.update(candidate.sources)
    metadata["candidateSources"] = sorted(sources)
    metadata["extractorSources"] = _extractor_sources_from_candidate_sources(sources)
    current_type_priority = float(metadata.get("entityTypePriority") or 0.0)
    if candidate.entity_type and candidate.entity_type_priority >= current_type_priority:
        entity.type = entity_type
        metadata["entityTypePriority"] = candidate.entity_type_priority

    aliases = set(entity.aliases or [])
    aliases.update(candidate.aliases)
    if _better_entity_name(candidate.name, entity.name):
        aliases.add(entity.name)
        entity.name = candidate.name
        entity.normalized_name = _normalize_entity(candidate.name)
        entity.description = _entity_description(candidate.name, entity.type, aliases, chunk.section_path)
    elif _normalize_entity(candidate.name) != _normalize_entity(entity.name):
        aliases.add(candidate.name)

    entity.aliases = sorted(_valid_aliases(aliases, entity.name))
    entity.description = _entity_description(entity.name, entity.type, set(entity.aliases), chunk.section_path)
    entity.confidence = max(float(entity.confidence or 0.0), _entity_confidence(candidate.score))
    entity.metadata = metadata


def _valid_aliases(aliases: set[str] | list[str], primary_name: str) -> set[str]:
    primary = _normalize_entity(primary_name)
    return {
        alias
        for alias in aliases or []
        if _valid_name(alias) and _normalize_entity(alias) != primary
    }


def _extractor_sources_from_candidate_sources(candidate_sources: set[str] | list[str]) -> list[str]:
    extractor_sources: set[str] = set()
    for source in candidate_sources or []:
        normalized = str(source or "").strip().lower()
        if not normalized:
            continue
        if normalized.startswith("ner"):
            extractor_sources.add(EXTRACTOR_NER)
        elif normalized.startswith("schema"):
            extractor_sources.add(EXTRACTOR_SCHEMA)
        elif "llm" in normalized:
            extractor_sources.add("llm-advisor")
        else:
            extractor_sources.add(EXTRACTOR_SCHEMA)
    return sorted(extractor_sources or {EXTRACTOR_SCHEMA})


def _primary_extractor_source(candidate_sources: set[str] | list[str]) -> str:
    extractor_sources = _extractor_sources_from_candidate_sources(candidate_sources)
    for preferred in (EXTRACTOR_NER, "llm-advisor", EXTRACTOR_SCHEMA):
        if preferred in extractor_sources:
            return preferred
    return extractor_sources[0] if extractor_sources else EXTRACTOR_SCHEMA


def _better_entity_name(candidate_name: str, current_name: str) -> bool:
    if not current_name:
        return True
    if _is_acronym(current_name) and not _is_acronym(candidate_name):
        return True
    if not _is_acronym(current_name) and _is_acronym(candidate_name):
        return False
    return len(candidate_name) > len(current_name) and len(candidate_name) <= 32

def _is_acronym(name: str) -> bool:
    return bool(re.fullmatch(r"[A-Z][A-Z0-9._-]{1,12}", name or ""))


def _english_acronym(name: str) -> str:
    words = re.findall(r"[A-Za-z][A-Za-z0-9]*", name or "")
    if len(words) < 2:
        return ""
    acronym = "".join(word[0].upper() for word in words if word)
    return acronym if 2 <= len(acronym) <= 10 else ""


def _entity_evidence(evidence_id: str, entity_id: str, chunk, text: str, candidate: CandidateName) -> GraphEvidence | None:
    quote_text = _candidate_quote(text, candidate)
    if not quote_text:
        return None
    return GraphEvidence(
        id=evidence_id,
        entityId=entity_id,
        chunkId=chunk.chunk_id,
        parentBlockId=chunk.parent_block_id,
        quoteText=quote_text,
        pageNo=chunk.page_no,
        pageRange=chunk.page_range,
        bboxJson=chunk.bbox_json,
        sectionPath=chunk.section_path,
        metadata={
            "chunkNo": chunk.chunk_no,
            "chunkType": chunk.chunk_type,
            "sourceBlockIds": chunk.source_block_ids,
            "candidateName": candidate.name,
            "candidateAliases": sorted(candidate.aliases),
            "candidateScore": round(candidate.score, 4),
            "candidateSources": sorted(candidate.sources),
            "extractorSources": _extractor_sources_from_candidate_sources(candidate.sources),
            "sourceType": _primary_extractor_source(candidate.sources),
            "grounded": True,
        },
    )


def _candidate_quote(text: str, candidate: CandidateName) -> str:
    return _quote(text, [candidate.name, *sorted(candidate.aliases)], fallback=False)


def _quote(text: str, names: list[str], fallback: bool = True) -> str:
    compact = re.sub(r"\s+", " ", text or "").strip()
    if not compact:
        return ""
    positions = [_find_name(compact, name) for name in names if name]
    positions = [position for position in positions if position >= 0]
    if not positions:
        return compact[:MAX_QUOTE_LENGTH] if fallback else ""
    center = min(positions)
    start = max(0, center - 90)
    end = min(len(compact), start + MAX_QUOTE_LENGTH)
    return compact[start:end].strip()


def _find_name(text: str, name: str) -> int:
    if not text or not name:
        return -1
    index = text.find(name)
    if index >= 0:
        return index
    return text.lower().find(name.lower())


def _hash(value: str) -> str:
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:16].upper()


def _normalize_entity(name: str) -> str:
    normalized = re.sub(r"\s+", "", name or "").lower()
    normalized = re.sub(r"[`*_#，,。；;：:（）()“”\"'\[\]{}<>《》/\\|-]+", "", normalized)
    return normalized


def _unique(items: list[Any]) -> list[Any]:
    result: list[Any] = []
    seen: set[Any] = set()
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def _sort_entities(entities) -> list[GraphEntity]:
    return sorted(entities, key=lambda entity: (-float(entity.confidence or 0.0), entity.type, entity.name.lower(), entity.id))


def _extract_metadata(
    entities: list[GraphEntity],
    relations: list[GraphRelation],
    evidences: list[GraphEvidence],
    valid_chunk_count: int,
) -> dict[str, Any]:
    layers = _extractor_layers()
    return {
        "extractionStatus": _extraction_status(
            layers,
            valid_chunk_count,
            len(entities),
            len(relations),
            len(evidences),
        ),
        "extractorLayers": layers,
        "extractorSourceCounts": _extractor_source_counts(entities, relations, evidences),
        "candidateSourceCounts": _candidate_source_counts(entities, relations, evidences),
    }


def _extractor_layers() -> list[dict[str, Any]]:
    return [
        {
            "name": "schema",
            "enabled": True,
            "status": "ready",
            "sources": ["schema.title", "schema.sectionPath", "schema.explicitAlias"],
        },
        {
            "name": "ner.lightweight",
            "enabled": True,
            "status": "ready",
            "sources": ["ner.pattern"],
        },
        _spacy_layer_status(),
        _ner_model_layer_status(),
    ]


def _extraction_status(
    layers: list[dict[str, Any]],
    valid_chunk_count: int,
    entity_count: int,
    relation_count: int,
    evidence_count: int,
) -> dict[str, Any]:
    reason_codes: list[str] = []
    unavailable_layers = [
        str(layer.get("name"))
        for layer in layers
        if layer.get("enabled") and layer.get("status") == "unavailable"
    ]
    if "ner.spacy" in unavailable_layers:
        reason_codes.append("SPACY_MODEL_UNAVAILABLE")
    if "ner.model" in unavailable_layers:
        reason_codes.append("NER_MODEL_UNAVAILABLE")
    if valid_chunk_count == 0:
        reason_codes.append("EMPTY_INPUT")
    elif entity_count == 0:
        reason_codes.append("NO_GROUNDED_ENTITY_CANDIDATE")
    if relation_count == 0 and valid_chunk_count > 0:
        reason_codes.append("NO_GROUNDED_RELATION_CANDIDATE")

    if valid_chunk_count == 0 or entity_count == 0:
        status = "empty"
    elif unavailable_layers:
        status = "completed_with_degradation"
    else:
        status = "completed"
    relation_reason = "EMPTY_INPUT" if valid_chunk_count == 0 else "NO_GROUNDED_RELATION_CANDIDATE"
    return {
        "schemaVersion": EXTRACTION_STATUS_SCHEMA,
        "status": status,
        "reasonCodes": reason_codes,
        "validChunkCount": valid_chunk_count,
        "entityCount": entity_count,
        "relationCount": relation_count,
        "evidenceCount": evidence_count,
        "entityStatus": "available" if entity_count else "empty",
        "relationStatus": "available" if relation_count else "empty",
        "relationReason": "" if relation_count else relation_reason,
    }


def _spacy_layer_status() -> dict[str, Any]:
    if _SPACY_NLP is not None:
        return {
            "name": "ner.spacy",
            "enabled": True,
            "status": "loaded",
            "modelName": _SPACY_MODEL_NAME,
        }
    if _SPACY_LOAD_ATTEMPTED:
        return {
            "name": "ner.spacy",
            "enabled": True,
            "status": "unavailable",
            "reason": "spaCy model not installed",
        }
    return {
        "name": "ner.spacy",
        "enabled": True,
        "status": "not_used",
        "reason": "no English-like text triggered spaCy probing",
    }


def _extractor_source_counts(
    entities: list[GraphEntity],
    relations: list[GraphRelation],
    evidences: list[GraphEvidence],
) -> dict[str, int]:
    counter: Counter[str] = Counter()
    for item in [*entities, *relations, *evidences]:
        for source in _metadata_list(item.metadata, "extractorSources"):
            counter[source] += 1
    return dict(sorted(counter.items()))


def _candidate_source_counts(
    entities: list[GraphEntity],
    relations: list[GraphRelation],
    evidences: list[GraphEvidence],
) -> dict[str, int]:
    counter: Counter[str] = Counter()
    for item in [*entities, *relations, *evidences]:
        for source in _metadata_list(item.metadata, "candidateSources"):
            counter[source] += 1
    return dict(sorted(counter.items()))


def _metadata_list(metadata: dict[str, Any] | None, key: str) -> list[str]:
    if not metadata:
        return []
    value = metadata.get(key)
    if isinstance(value, list):
        return [str(item) for item in value if item]
    if isinstance(value, str) and value.strip():
        return [value.strip()]
    return []
