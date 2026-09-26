#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CREATE_TABLE_SQL="${REPO_ROOT}/sql/表结构/Mysql/create_table_mysql.sql"
INIT_SQL="${REPO_ROOT}/sql/表数据/Mysql/init_system_config.sql"
MYSQL_BIN="${SYSTEM_CONFIG_SQL_TEST_MYSQL_BIN:-$(command -v mysql)}"
MYSQL_HOST="${SYSTEM_CONFIG_SQL_TEST_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${SYSTEM_CONFIG_SQL_TEST_MYSQL_PORT:-3306}"
MYSQL_USER="${SYSTEM_CONFIG_SQL_TEST_MYSQL_USER:-root}"
export MYSQL_PWD="${SYSTEM_CONFIG_SQL_TEST_MYSQL_PASSWORD:-root}"

DB_NAME="nexus_agent_system_config_fixture_${$}_${RANDOM}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/system-config-init-sql-test.XXXXXX")"
FIXTURE_CREATE_TABLE_SQL="${TMP_ROOT}/create_table_mysql.sql"
FIXTURE_INIT_SQL="${TMP_ROOT}/init_system_config.sql"
MYSQL=(
  "${MYSQL_BIN}"
  --protocol=TCP
  --host="${MYSQL_HOST}"
  --port="${MYSQL_PORT}"
  --user="${MYSQL_USER}"
  --batch
  --skip-column-names
  --default-character-set=utf8mb4
)

cleanup() {
  "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`" >/dev/null 2>&1 || true
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

sed "s/^USE nexus_agent_pro;$/USE \`${DB_NAME}\`;/" "${CREATE_TABLE_SQL}" > "${FIXTURE_CREATE_TABLE_SQL}"
sed "s/^USE nexus_agent_pro;$/USE \`${DB_NAME}\`;/" "${INIT_SQL}" > "${FIXTURE_INIT_SQL}"
if ! grep -Fqx "USE \`${DB_NAME}\`;" "${FIXTURE_CREATE_TABLE_SQL}" \
  || ! grep -Fqx "USE \`${DB_NAME}\`;" "${FIXTURE_INIT_SQL}"; then
  echo "failed to bind system config SQL to fixture database" >&2
  exit 1
fi

query_scalar() {
  "${MYSQL[@]}" "${DB_NAME}" -e "$1"
}

assert_equals() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${label}: expected ${expected}, got ${actual}" >&2
    exit 1
  fi
}

run_init() {
  "${MYSQL[@]}" "${DB_NAME}" < "${FIXTURE_INIT_SQL}"
}

"${MYSQL[@]}" -e "CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
"${MYSQL[@]}" "${DB_NAME}" < "${FIXTURE_CREATE_TABLE_SQL}"

run_init

assert_equals "seed row count" "1" "$(query_scalar "SELECT COUNT(*) FROM nexus_agent_system_config")"
assert_equals "snapshot value count" "101" "$(query_scalar "SELECT JSON_LENGTH(config_json, '$.values') FROM nexus_agent_system_config WHERE id = 1")"
assert_equals "config group count" "4" "$(query_scalar "SELECT COUNT(*) FROM nexus_agent_system_config_group WHERE status = 1")"
assert_equals "config category count" "14" "$(query_scalar "SELECT COUNT(*) FROM nexus_agent_system_config_category WHERE status = 1")"
assert_equals "config group order" "retrieval,conversation,documentBuild,graphRag" "$(query_scalar "SELECT GROUP_CONCAT(group_key ORDER BY sort_order, id) FROM nexus_agent_system_config_group WHERE status = 1")"
assert_equals "retrieval category order" "retrievalWindow,execution,relevance,channels,fusion" "$(query_scalar "SELECT GROUP_CONCAT(category_key ORDER BY sort_order, id) FROM nexus_agent_system_config_category WHERE group_key = 'retrieval' AND status = 1")"

VALID_DEFAULTS="$(query_scalar "
SELECT COUNT(*)
FROM nexus_agent_system_config
WHERE id = 1
  AND config_version = 0
  AND status = 1
  AND JSON_EXTRACT(config_json, '$.schemaVersion') = 2
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.vectorTopK\"') = 10
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.keywordTopK\"') = 10
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.graphRagTopK\"') = 5
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.graphRagMaxHops\"') = 2
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.raptorTopK\"') = 5
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.raptorSourceChunkTopK\"') = 3
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.candidateTopK\"') = 40
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.rerankCandidateTopK\"') = 24
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.finalTopK\"') = 6
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"ragRuntime.rerankEnabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.channelTimeoutMs\"') = 12000
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.subQuestionTimeoutMs\"') = 40000
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.minVectorSimilarity\"') + 0 = 0.45
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.keywordRelativeScoreFloor\"') + 0 = 0.35
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"ragRuntime.keywordChannelEnabled\"')) = 'true'
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"ragRuntime.tableChannelEnabled\"')) = 'true'
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"ragRuntime.graphRagChannelEnabled\"')) = 'true'
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"ragRuntime.raptorChannelEnabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.vectorWeight\"') + 0 = 1.0
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.keywordWeight\"') + 0 = 1.0
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.tableWeight\"') + 0 = 1.2
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.graphRagWeight\"') + 0 = 1.1
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.raptorWeight\"') + 0 = 1.05
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.rankWeight\"') + 0 = 1.0
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.originalScoreWeight\"') + 0 = 0.08
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.metadataBoostWeight\"') + 0 = 0.04
  AND JSON_EXTRACT(config_json, '$.values.\"ragRuntime.hybrid.maxMetadataBoost\"') + 0 = 1.0
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"chat.recommendationEnabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"chat.maxModelCallsPerRun\"') = 8
  AND JSON_CONTAINS_PATH(config_json, 'one', '$.values.\"chat.systemPrompt\"') = 0
  AND JSON_CONTAINS_PATH(config_json, 'one', '$.values.\"chat.recommendationPrompt\"') = 0
  AND JSON_CONTAINS_PATH(config_json, 'one', '$.values.\"rag.answerSystemPrompt\"') = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"rag.rewriteEnabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"rag.rewriteOptions.temperature\"') + 0 = 0.2
  AND JSON_EXTRACT(config_json, '$.values.\"rag.raptorMaxClusterSize\"') = 6
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"graphRag.build.leaseEnabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"graphRag.build.leaseTtlSeconds\"') = 6000
  AND JSON_EXTRACT(config_json, '$.values.\"graphRag.extraction.batchTokenLimit\"') = 2800
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"chunkEnrichment.enabled\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"chunkEnrichment.maxKeywords\"') = 8
  AND JSON_EXTRACT(config_json, '$.values.\"indexBuild.embeddingBatchSize\"') = 5
  AND JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.values.\"indexBuild.elasticsearchRefreshWait\"')) = 'true'
  AND JSON_EXTRACT(config_json, '$.values.\"chunk.recursiveMaxChars\"') = 800
  AND JSON_EXTRACT(config_json, '$.values.\"chunk.semanticSimilarityThreshold\"') + 0 = 0.18
")"
assert_equals "complete default snapshot" "1" "${VALID_DEFAULTS}"

query_scalar "
UPDATE nexus_agent_system_config
SET config_version = 7,
    config_json = JSON_SET(config_json, '$.values.\"ragRuntime.vectorTopK\"', 19)
WHERE id = 1
" >/dev/null

query_scalar "
UPDATE nexus_agent_system_config_group
SET group_label = '自定义检索分类', sort_order = 15
WHERE group_key = 'retrieval'
" >/dev/null

query_scalar "
UPDATE nexus_agent_system_config_category
SET category_label = '自定义召回分类', sort_order = 15
WHERE category_key = 'retrievalWindow'
" >/dev/null

run_init

assert_equals "second run row count" "1" "$(query_scalar "SELECT COUNT(*) FROM nexus_agent_system_config")"
assert_equals "existing version preserved" "7" "$(query_scalar "SELECT config_version FROM nexus_agent_system_config WHERE id = 1")"
assert_equals "existing value preserved" "19" "$(query_scalar "SELECT JSON_EXTRACT(config_json, '$.values.\"ragRuntime.vectorTopK\"') FROM nexus_agent_system_config WHERE id = 1")"
assert_equals "existing group metadata preserved" "自定义检索分类:15" "$(query_scalar "SELECT CONCAT(group_label, ':', sort_order) FROM nexus_agent_system_config_group WHERE group_key = 'retrieval'")"
assert_equals "existing category metadata preserved" "自定义召回分类:15" "$(query_scalar "SELECT CONCAT(category_label, ':', sort_order) FROM nexus_agent_system_config_category WHERE category_key = 'retrievalWindow'")"

echo "system config initialization SQL: PASS"
