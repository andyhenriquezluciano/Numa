CREATE TABLE IF NOT EXISTS library_states (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision INTEGER NOT NULL CHECK (revision > 0),
    payload TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
