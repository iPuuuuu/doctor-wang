"""PgVectorStore 自动重连测试：连接失效后重建。"""
import pytest
from app.vectorstore import PgVectorStore


class _BrokenConn:
    def __init__(self): self.closed = False
    def cursor(self): raise ConnectionError("connection lost")
    def commit(self): pass


class _GoodConn:
    def __init__(self): self.pinged = 0
    def cursor(self):
        class Cur:
            def execute(self, sql, params=None): pass
            def fetchall(self): return []
            def __enter__(self): return self
            def __exit__(self, *a): return False
        self.pinged += 1
        return Cur()
    def commit(self): pass


def test_reconnects_when_connection_broken(monkeypatch):
    import sys, types
    broken = _BrokenConn()
    good = _GoodConn()
    fake_psycopg = types.ModuleType("psycopg")
    fake_psycopg.connect = lambda url: good
    monkeypatch.setitem(sys.modules, "psycopg", fake_psycopg)
    store = PgVectorStore(broken, table="ai_vector_chunk", database_url="postgresql://x")
    # search 应先重连再查询（不再抛 ConnectionError）
    assert store.search("ns", [0.0] * 4, top_k=1) == []
    assert good.pinged >= 1


def test_raises_when_no_url_for_reconnect():
    store = PgVectorStore(_BrokenConn(), table="ai_vector_chunk", database_url=None)
    with pytest.raises(Exception):
        store.search("ns", [0.0] * 4, top_k=1)
