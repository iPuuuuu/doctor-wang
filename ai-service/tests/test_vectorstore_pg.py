"""PgVectorStore 契约测试：SQL 语句与参数形状（mock 连接，不触网、无真实 PostgreSQL）。

验证表契约（Flyway V9）：namespace/chunk_id/content/embedding vector/metadata jsonb。
"""
import json

from app.vectorstore import PgVectorStore, VectorRecord


class FakeCursor:
    def __init__(self): self.executed = []

    def execute(self, sql, params=None):
        self.executed.append((sql, params))

    def fetchall(self): return []
    def __enter__(self): return self
    def __exit__(self, *a): return False


class FakeConnection:
    def __init__(self): self.cursor_instance = FakeCursor()

    def cursor(self): return self.cursor_instance

    def commit(self): pass


def test_upsert_uses_vector_and_jsonb_contract():
    conn = FakeConnection()
    store = PgVectorStore(conn, table="ai_vector_chunk")
    store.upsert([VectorRecord(chunk_id="c1", namespace="report:1:2", vector=[0.1, 0.2], text="片段", metadata={"page_no": 1, "confidence": 0.9})])

    sql, params = conn.cursor_instance.executed[0]
    assert "INSERT INTO ai_vector_chunk (namespace, chunk_id, content, embedding, metadata)" in sql
    assert "ON CONFLICT (namespace, chunk_id) DO UPDATE" in sql
    assert "::vector" in sql and "::jsonb" in sql
    assert params[0] == "report:1:2"
    assert params[2] == "片段"
    assert params[3] == "[0.10000000,0.20000000]"
    assert json.loads(params[4]) == {"page_no": 1, "confidence": 0.9}


def test_search_filters_namespace_and_orders_by_cosine_distance():
    conn = FakeConnection()
    store = PgVectorStore(conn, table="ai_vector_chunk")
    store.search(namespace="report:1:2", query=[0.5, 0.5], top_k=5)

    sql, params = conn.cursor_instance.executed[0]
    assert "embedding <=> %s::vector" in sql
    assert "WHERE namespace=%s" in sql
    assert params[1] == "report:1:2"


def test_delete_namespace_scoped():
    conn = FakeConnection()
    store = PgVectorStore(conn, table="ai_vector_chunk")
    store.delete_namespace("report:1:2")
    sql, params = conn.cursor_instance.executed[0]
    assert "DELETE FROM ai_vector_chunk WHERE namespace=%s" in sql
    assert params[0] == "report:1:2"


def test_invalid_table_name_rejected():
    conn = FakeConnection()
    try:
        PgVectorStore(conn, table="ai_vector_chunk; drop table users")
        raise AssertionError("expected ValueError")
    except ValueError:
        pass
