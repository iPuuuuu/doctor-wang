import json
import os
import sqlite3
from datetime import datetime, timezone


class JobRepository:
    def __init__(self, path: str):
        self.path = path
        directory = os.path.dirname(path)
        if directory: os.makedirs(directory, exist_ok=True)
        with self._connect() as connection:
            connection.execute("CREATE TABLE IF NOT EXISTS ai_jobs (job_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at TEXT NOT NULL)")

    def _connect(self): return sqlite3.connect(self.path)

    def save(self, job_id: str, payload: dict) -> None:
        now = datetime.now(timezone.utc).isoformat()
        with self._connect() as connection:
            connection.execute("INSERT OR REPLACE INTO ai_jobs(job_id,payload,updated_at) VALUES(?,?,?)", (job_id, json.dumps(payload), now))

    def get(self, job_id: str) -> dict | None:
        with self._connect() as connection:
            row = connection.execute("SELECT payload FROM ai_jobs WHERE job_id=?", (job_id,)).fetchone()
        return json.loads(row[0]) if row else None
