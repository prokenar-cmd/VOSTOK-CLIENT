from __future__ import annotations

import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator, Optional


SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, display_name TEXT NOT NULL, email TEXT, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS identities (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, provider TEXT NOT NULL, subject TEXT NOT NULL, email TEXT, created_at INTEGER NOT NULL, UNIQUE(provider, subject));
CREATE TABLE IF NOT EXISTS email_challenges (challenge_id TEXT PRIMARY KEY, email TEXT NOT NULL, code_hash TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, resend_after INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, consumed_at INTEGER);
CREATE INDEX IF NOT EXISTS idx_email_challenges_email_created ON email_challenges(email, created_at);
CREATE TABLE IF NOT EXISTS refresh_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER);
CREATE TABLE IF NOT EXISTS characters (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, name TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 1, skin_id INTEGER NOT NULL DEFAULT 0, server_id TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(server_id, name));
CREATE INDEX IF NOT EXISTS idx_characters_account ON characters(account_id);
CREATE TABLE IF NOT EXISTS game_tickets (id INTEGER PRIMARY KEY AUTOINCREMENT, token_hash TEXT NOT NULL UNIQUE, account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE, server_id TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, consumed_at INTEGER);
"""


class Database:
    def __init__(self, path: str):
        self.path = path
        self._init_lock = threading.Lock()
        self._initialized = False

    def initialize(self) -> None:
        with self._init_lock:
            if self._initialized:
                return
            if self.path != ":memory:":
                Path(self.path).parent.mkdir(parents=True, exist_ok=True)
            with self.connect() as conn:
                conn.executescript(SCHEMA)
                conn.commit()
            self._initialized = True

    @contextmanager
    def connect(self) -> Iterator[sqlite3.Connection]:
        conn = sqlite3.connect(self.path, timeout=30, isolation_level=None)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        conn.execute("PRAGMA busy_timeout=5000")
        if self.path != ":memory:":
            conn.execute("PRAGMA journal_mode=WAL")
        try:
            yield conn
        finally:
            conn.close()

    def transaction(self):
        return _Transaction(self)

    def find_identity(self, provider: str, subject: str) -> Optional[sqlite3.Row]:
        with self.connect() as conn:
            return conn.execute("SELECT * FROM identities WHERE provider=? AND subject=?", (provider, subject)).fetchone()

    def get_account(self, account_id: int) -> Optional[sqlite3.Row]:
        with self.connect() as conn:
            return conn.execute("SELECT * FROM accounts WHERE id=?", (account_id,)).fetchone()

    def create_account_with_identity(self, provider: str, subject: str, email: str | None, display_name: str, now: int) -> int:
        with self.transaction() as conn:
            existing = conn.execute("SELECT account_id FROM identities WHERE provider=? AND subject=?", (provider, subject)).fetchone()
            if existing:
                return int(existing["account_id"])
            cur = conn.execute("INSERT INTO accounts(display_name,email,created_at) VALUES(?,?,?)", (display_name, email, now))
            account_id = int(cur.lastrowid)
            conn.execute("INSERT INTO identities(account_id,provider,subject,email,created_at) VALUES(?,?,?,?,?)", (account_id, provider, subject, email, now))
            return account_id

    def list_characters(self, account_id: int) -> list[sqlite3.Row]:
        with self.connect() as conn:
            return list(conn.execute("SELECT * FROM characters WHERE account_id=? ORDER BY id", (account_id,)).fetchall())

    def get_character_for_account(self, account_id: int, character_id: int) -> Optional[sqlite3.Row]:
        with self.connect() as conn:
            return conn.execute("SELECT * FROM characters WHERE id=? AND account_id=?", (character_id, account_id)).fetchone()

    def create_character_for_test(self, account_id: int, name: str, server_id: str, now: int, skin_id: int = 0) -> int:
        with self.transaction() as conn:
            cur = conn.execute("INSERT INTO characters(account_id,name,level,skin_id,server_id,created_at) VALUES(?,?,?,?,?,?)", (account_id, name, 1, skin_id, server_id, now))
            return int(cur.lastrowid)


class _Transaction:
    def __init__(self, db: Database):
        self.db = db
        self.conn: sqlite3.Connection | None = None

    def __enter__(self) -> sqlite3.Connection:
        self.conn = sqlite3.connect(self.db.path, timeout=30, isolation_level=None)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA foreign_keys=ON")
        self.conn.execute("PRAGMA busy_timeout=5000")
        self.conn.execute("BEGIN IMMEDIATE")
        return self.conn

    def __exit__(self, exc_type, exc, tb):
        assert self.conn is not None
        try:
            self.conn.execute("ROLLBACK" if exc_type else "COMMIT")
        finally:
            self.conn.close()
