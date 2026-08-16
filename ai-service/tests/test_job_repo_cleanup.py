"""JobRepository 过期清理测试。"""
import tempfile
from datetime import datetime, timedelta, timezone
from app.jobs import JobRepository


def _old_iso(days: int) -> str:
    return (datetime.now(timezone.utc) - timedelta(days=days)).isoformat()


def test_delete_older_than_removes_stale_only():
    with tempfile.NamedTemporaryFile(suffix=".sqlite3", delete=False) as f:
        path = f.name
    try:
        repo = JobRepository(path)
        # 直接插入不同 updated_at 的行
        import sqlite3
        with sqlite3.connect(path) as conn:
            conn.execute("INSERT INTO ai_jobs VALUES ('old', '{}', ?)", (_old_iso(40),))
            conn.execute("INSERT INTO ai_jobs VALUES ('fresh', '{}', ?)", (_old_iso(1),))
        assert repo.delete_older_than(30) == 1
        assert repo.get("old") is None
        assert repo.get("fresh") is not None
    finally:
        import os
        os.unlink(path)


def test_delete_older_than_no_rows():
    with tempfile.NamedTemporaryFile(suffix=".sqlite3", delete=False) as f:
        path = f.name
    try:
        repo = JobRepository(path)
        assert repo.delete_older_than(30) == 0
    finally:
        import os
        os.unlink(path)
