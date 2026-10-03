#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
INIT_SQL="${REPO_ROOT}/sql/表数据/Mysql/知识路由初始化(需要文档例子演示中文档上传解析完).sql"
MYSQL_BIN="${RAG_SQL_TEST_MYSQL_BIN:-$(command -v mysql)}"
MYSQL_HOST="${RAG_SQL_TEST_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${RAG_SQL_TEST_MYSQL_PORT:-3306}"
MYSQL_USER="${RAG_SQL_TEST_MYSQL_USER:-root}"
export MYSQL_PWD="${RAG_SQL_TEST_MYSQL_PASSWORD:-root}"

DB_NAME="aegis_kb_s16_route_fixture_${$}_${RANDOM}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/rag-route-init-sql-test.XXXXXX")"
FIXTURE_INIT_SQL="${TMP_ROOT}/知识路由初始化脚本.sql"
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

sed "s/^USE aegis_kb;$/USE \`${DB_NAME}\`;/" "${INIT_SQL}" > "${FIXTURE_INIT_SQL}"
if ! grep -Fqx "USE \`${DB_NAME}\`;" "${FIXTURE_INIT_SQL}"; then
  echo "failed to bind route initialization SQL to fixture database" >&2
  exit 1
fi

"${MYSQL[@]}" -e "CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"

"${MYSQL[@]}" "${DB_NAME}" <<'SQL'
CREATE TABLE aegis_kb_knowledge_base (
    id BIGINT NOT NULL PRIMARY KEY,
    base_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aegis_kb_document (
    id BIGINT NOT NULL PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    parse_status TINYINT NOT NULL,
    strategy_status TINYINT NOT NULL,
    index_status TINYINT NOT NULL,
    last_index_task_id BIGINT NULL,
    knowledge_base_id BIGINT NOT NULL,
    knowledge_base_name VARCHAR(128) NOT NULL,
    create_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aegis_kb_knowledge_scope_node (
    id BIGINT NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    scope_name VARCHAR(128) NOT NULL,
    parent_scope_id BIGINT NULL,
    description VARCHAR(1024) NULL,
    aliases VARCHAR(512) NULL,
    examples TEXT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME NULL,
    edit_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aegis_kb_knowledge_topic_node (
    id BIGINT NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    topic_name VARCHAR(128) NOT NULL,
    scope_id BIGINT NOT NULL,
    description VARCHAR(1024) NULL,
    aliases VARCHAR(512) NULL,
    examples TEXT NULL,
    answer_shape VARCHAR(64) NULL,
    execution_preference VARCHAR(64) NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME NULL,
    edit_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aegis_kb_document_profile (
    id BIGINT NOT NULL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    profile_version INT DEFAULT 1,
    document_summary TEXT NULL,
    document_type VARCHAR(64) NULL,
    core_topics TEXT NULL,
    example_questions TEXT NULL,
    graph_friendly TINYINT DEFAULT 0,
    supports_graph_outline TINYINT DEFAULT 0,
    supports_item_lookup TINYINT DEFAULT 0,
    supports_graph_assist TINYINT DEFAULT 0,
    profile_source VARCHAR(64) NULL,
    profile_status TINYINT DEFAULT 1,
    error_msg VARCHAR(1024) NULL,
    create_time DATETIME NULL,
    edit_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aegis_kb_topic_document_relation (
    id BIGINT NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    relation_score DECIMAL(8,4) DEFAULT 0,
    relation_source VARCHAR(64) NULL,
    reason VARCHAR(1024) NULL,
    create_time DATETIME NULL,
    edit_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_base_topic_document (knowledge_base_id, topic_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
SQL

seed_run() {
  local run_code="$1"
  local kb_base="$2"
  local doc_base="$3"
  "${MYSQL[@]}" "${DB_NAME}" <<SQL
SET @run_code = '${run_code}';
SET @kb_parse = ${kb_base} + 1;
SET @kb_operation = ${kb_base} + 2;
SET @kb_graph = ${kb_base} + 3;
SET @parse_name = IF(@run_code = '', '解析回归知识库', CONCAT('解析回归知识库 [', @run_code, ']'));
SET @operation_name = IF(@run_code = '', '运营制度知识库', CONCAT('运营制度知识库 [', @run_code, ']'));
SET @graph_name = IF(@run_code = '', 'GraphRAG图谱评测知识库', CONCAT('GraphRAG图谱评测知识库 [', @run_code, ']'));

INSERT INTO aegis_kb_knowledge_base (id, base_name, status) VALUES
(@kb_parse, @parse_name, 1),
(@kb_operation, @operation_name, 1),
(@kb_graph, @graph_name, 1);

INSERT INTO aegis_kb_document
    (id, document_name, original_file_name, parse_status, strategy_status, index_status,
     last_index_task_id, knowledge_base_id, knowledge_base_name, create_time, status)
VALUES
(${doc_base} + 1, 'O2-provider-artifact验收样例.pdf', 'O2-provider-artifact验收样例.pdf', 3, 3, 3, ${doc_base} + 101, @kb_parse, @parse_name, NOW(), 1),
(${doc_base} + 2, 'O2-扫描OCR验收样例-图片型PDF.pdf', 'O2-扫描OCR验收样例-图片型PDF.pdf', 3, 3, 3, ${doc_base} + 102, @kb_parse, @parse_name, NOW(), 1),
(${doc_base} + 3, 'O2-扫描OCR验收样例-文字截图.png', 'O2-扫描OCR验收样例-文字截图.png', 3, 3, 3, ${doc_base} + 103, @kb_parse, @parse_name, NOW(), 1),
(${doc_base} + 4, '星联智服全渠道客服平台上线与运营管理手册.md', '星联智服全渠道客服平台上线与运营管理手册.md', 3, 3, 3, ${doc_base} + 104, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 5, '生产环境发布与回滚操作规范.md', '生产环境发布与回滚操作规范.md', 3, 3, 3, ${doc_base} + 105, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 6, '核心业务系统故障应急响应预案.md', '核心业务系统故障应急响应预案.md', 3, 3, 3, ${doc_base} + 106, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 7, '客户数据分级与访问控制管理制度.md', '客户数据分级与访问控制管理制度.md', 3, 3, 3, ${doc_base} + 107, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 8, '差旅与费用报销管理办法.md', '差旅与费用报销管理办法.md', 3, 3, 3, ${doc_base} + 108, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 9, '澄星智能新员工入职培训手册.md', '澄星智能新员工入职培训手册.md', 3, 3, 3, ${doc_base} + 109, @kb_operation, @operation_name, NOW(), 1),
(${doc_base} + 10, 'O6跨文档图谱-审计证据规范A.md', 'O6跨文档图谱-审计证据规范A.md', 3, 3, 3, ${doc_base} + 110, @kb_graph, @graph_name, NOW(), 1),
(${doc_base} + 11, 'O6跨文档图谱-审计系统别名说明B.md', 'O6跨文档图谱-审计系统别名说明B.md', 3, 3, 3, ${doc_base} + 111, @kb_graph, @graph_name, NOW(), 1),
(${doc_base} + 12, 'O6多社区排序-生产发布回滚规范A.md', 'O6多社区排序-生产发布回滚规范A.md', 3, 3, 3, ${doc_base} + 112, @kb_graph, @graph_name, NOW(), 1),
(${doc_base} + 13, 'O6多社区排序-生产发布回滚别名B.md', 'O6多社区排序-生产发布回滚别名B.md', 3, 3, 3, ${doc_base} + 113, @kb_graph, @graph_name, NOW(), 1),
(${doc_base} + 14, 'O6多社区排序-客户数据访问控制规范A.md', 'O6多社区排序-客户数据访问控制规范A.md', 3, 3, 3, ${doc_base} + 114, @kb_graph, @graph_name, NOW(), 1),
(${doc_base} + 15, 'O6多社区排序-客户数据访问控制别名B.md', 'O6多社区排序-客户数据访问控制别名B.md', 3, 3, 3, ${doc_base} + 115, @kb_graph, @graph_name, NOW(), 1);
SQL
}

run_init() {
  local run_code="$1"
  local base_id="$2"
  local output_file="$3"
  local run_init_sql="${TMP_ROOT}/${run_code}.init.sql"

  # Generate isolated names/IDs while keeping the production script parameter-free.
  sed \
    -e "s/^SET @base_id = 9000000000000000000;$/SET @base_id = ${base_id};/" \
    -e "s/^SET @kb_parse_name = '解析回归知识库';$/SET @kb_parse_name = '解析回归知识库 [${run_code}]';/" \
    -e "s/^SET @kb_operation_name = '运营制度知识库';$/SET @kb_operation_name = '运营制度知识库 [${run_code}]';/" \
    -e "s/^SET @kb_graph_name = 'GraphRAG图谱评测知识库';$/SET @kb_graph_name = 'GraphRAG图谱评测知识库 [${run_code}]';/" \
    "${FIXTURE_INIT_SQL}" > "${run_init_sql}"

  "${MYSQL[@]}" "${DB_NAME}" >"${output_file}" 2>&1 <<SQL
SOURCE ${run_init_sql};
SQL
}

run_init_without_parameters() {
  local output_file="$1"
  "${MYSQL[@]}" "${DB_NAME}" >"${output_file}" 2>&1 <<SQL
SOURCE ${FIXTURE_INIT_SQL};
SQL
}

expect_success() {
  local run_code="$1"
  local base_id="$2"
  local output_file="$3"
  if ! run_init "${run_code}" "${base_id}" "${output_file}"; then
    echo "expected initialization success for ${run_code}" >&2
    sed -n '1,200p' "${output_file}" >&2
    exit 1
  fi
}

expect_failure() {
  local run_code="$1"
  local base_id="$2"
  local marker="$3"
  local output_file="${TMP_ROOT}/${run_code}.failure"
  if run_init "${run_code}" "${base_id}" "${output_file}"; then
    echo "expected initialization failure for ${run_code}" >&2
    exit 1
  fi
  if ! grep -q "${marker}" "${output_file}"; then
    echo "missing failure marker '${marker}' for ${run_code}" >&2
    sed -n '1,160p' "${output_file}" >&2
    exit 1
  fi
}

assert_count() {
  local table_name="$1"
  local expected="$2"
  local actual
  actual="$("${MYSQL[@]}" "${DB_NAME}" -e "SELECT COUNT(*) FROM ${table_name}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${table_name}: expected ${expected}, got ${actual}" >&2
    exit 1
  fi
}

# Direct execution needs no session parameters and matches the three fixed KB names.
seed_run "" 900 9000
run_init_without_parameters "${TMP_ROOT}/default-run.first"
assert_count aegis_kb_knowledge_scope_node 3
assert_count aegis_kb_knowledge_topic_node 14
assert_count aegis_kb_document_profile 15
assert_count aegis_kb_topic_document_relation 26

seed_run "run-a" 1000 10000
expect_success "run-a" 7000000000000000000 "${TMP_ROOT}/run-a.first"
assert_count aegis_kb_knowledge_scope_node 6
assert_count aegis_kb_knowledge_topic_node 28
assert_count aegis_kb_document_profile 30
assert_count aegis_kb_topic_document_relation 52

# Same run and ID segment is an idempotent replay.
expect_success "run-a" 7000000000000000000 "${TMP_ROOT}/run-a.replay"
assert_count aegis_kb_knowledge_scope_node 6
assert_count aegis_kb_knowledge_topic_node 28
assert_count aegis_kb_document_profile 30
assert_count aegis_kb_topic_document_relation 52

# A distinct run with a distinct ID segment does not overwrite the first cohort.
seed_run "run-b" 2000 20000
expect_success "run-b" 7000000000000010000 "${TMP_ROOT}/run-b.first"
assert_count aegis_kb_knowledge_scope_node 9
assert_count aegis_kb_knowledge_topic_node 42
assert_count aegis_kb_document_profile 45
assert_count aegis_kb_topic_document_relation 78

# One missing document in each 3/6/6 group fails before permanent route writes.
seed_run "missing-parse" 3000 30000
"${MYSQL[@]}" "${DB_NAME}" -e "DELETE FROM aegis_kb_document WHERE id = 30001"
expect_failure "missing-parse" 7000000000000020000 "以下文档未在预期知识库"

seed_run "missing-operation" 4000 40000
"${MYSQL[@]}" "${DB_NAME}" -e "DELETE FROM aegis_kb_document WHERE id = 40004"
expect_failure "missing-operation" 7000000000000030000 "以下文档未在预期知识库"

seed_run "missing-graph" 5000 50000
"${MYSQL[@]}" "${DB_NAME}" -e "DELETE FROM aegis_kb_document WHERE id = 50010"
expect_failure "missing-graph" 7000000000000040000 "以下文档未在预期知识库"

# Duplicate KB names and duplicate expected documents are ambiguous, never latest-wins.
seed_run "duplicate-kb" 6000 60000
"${MYSQL[@]}" "${DB_NAME}" -e \
  "INSERT INTO aegis_kb_knowledge_base VALUES (6999, '解析回归知识库 [duplicate-kb]', 1)"
expect_failure "duplicate-kb" 7000000000000050000 "存在多个启用记录"

seed_run "duplicate-doc" 7000 70000
"${MYSQL[@]}" "${DB_NAME}" -e \
  "INSERT INTO aegis_kb_document SELECT 79999, document_name, original_file_name, parse_status, strategy_status, index_status, last_index_task_id, knowledge_base_id, knowledge_base_name, NOW(), status FROM aegis_kb_document WHERE id = 70001"
expect_failure "duplicate-doc" 7000000000000060000 "存在多个有效记录"

# A reserved ID owned by unrelated data fails without overwriting that row.
seed_run "id-conflict" 8000 80000
"${MYSQL[@]}" "${DB_NAME}" -e \
  "INSERT INTO aegis_kb_knowledge_scope_node (id, knowledge_base_id, scope_name, status) VALUES (7000000000000070001, 1, 'unrelated', 1)"
expect_failure "id-conflict" 7000000000000070000 "目标 ID 段存在归属冲突"
test "$("${MYSQL[@]}" "${DB_NAME}" -e "SELECT scope_name FROM aegis_kb_knowledge_scope_node WHERE id = 7000000000000070001")" = "unrelated"

echo "rag route initialization SQL self-test: PASS"
