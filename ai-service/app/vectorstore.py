import math
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class VectorRecord:
    chunk_id: str
    namespace: str
    vector: list[float]
    text: str
    metadata: dict[str, Any]


class InMemoryVectorStore:
    def __init__(self): self.records: dict[tuple[str, str], VectorRecord] = {}

    def upsert(self, records: list[VectorRecord]) -> None:
        for record in records: self.records[(record.namespace, record.chunk_id)] = record

    def delete_namespace(self, namespace: str) -> None:
        self.records = {key: value for key, value in self.records.items() if value.namespace != namespace}

    def search(self, namespace: str, query: list[float], top_k: int = 5) -> list[tuple[VectorRecord, float]]:
        def score(record: VectorRecord) -> float:
            return sum(a * b for a, b in zip(record.vector, query)) / ((sum(a*a for a in record.vector) ** .5 or 1) * (sum(a*a for a in query) ** .5 or 1))
        return sorted(((r, score(r)) for r in self.records.values() if r.namespace == namespace), key=lambda x: x[1], reverse=True)[:top_k]


class PgVectorStore:
    """pgvector adapter; the backend owns creation/migration of this table."""
    def __init__(self, connection, table: str = "ai_vector_chunk"):
        if not table.replace("_", "").isalnum():
            raise ValueError("invalid vector table name")
        self.connection, self.table = connection, table

    def upsert(self, records: list[VectorRecord]) -> None:
        sql = f"""INSERT INTO {self.table} (namespace, chunk_id, content, embedding, metadata)
                  VALUES (%s, %s, %s, %s::vector, %s::jsonb)
                  ON CONFLICT (namespace, chunk_id) DO UPDATE SET content=EXCLUDED.content,
                  embedding=EXCLUDED.embedding, metadata=EXCLUDED.metadata"""
        import json
        with self.connection.cursor() as cursor:
            for record in records:
                cursor.execute(sql, (record.namespace, record.chunk_id, record.text, _vector_literal(record.vector), json.dumps(record.metadata)))
        self.connection.commit()

    def search(self, namespace: str, query: list[float], top_k: int = 5):
        sql = f"SELECT chunk_id, namespace, content, embedding <=> %s::vector AS distance, metadata FROM {self.table} WHERE namespace=%s ORDER BY embedding <=> %s::vector LIMIT %s"
        rows = []
        with self.connection.cursor() as cursor:
            literal = _vector_literal(query)
            cursor.execute(sql, (literal, namespace, literal, top_k))
            rows = cursor.fetchall()
        import json
        return [(VectorRecord(row[0], row[1], [], row[2], row[4] if isinstance(row[4], dict) else json.loads(row[4])), 1 - float(row[3])) for row in rows]

    def delete_namespace(self, namespace: str) -> None:
        with self.connection.cursor() as cursor:
            cursor.execute(f"DELETE FROM {self.table} WHERE namespace=%s", (namespace,))
        self.connection.commit()


def _vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in vector) + "]"


def create_vector_store(database_url: str | None = None) -> InMemoryVectorStore | PgVectorStore:
    """Factory: returns PgVectorStore if database_url is configured, else InMemoryVectorStore.

    PgVectorStore connects to the backend-owned ai_vector_chunk table.
    InMemoryVectorStore is used for development/testing when no PostgreSQL is available.
    """
    if database_url:
        import psycopg2
        conn = psycopg2.connect(database_url)
        return PgVectorStore(conn, table="ai_vector_chunk")
    return InMemoryVectorStore()
