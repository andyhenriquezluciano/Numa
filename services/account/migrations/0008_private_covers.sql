CREATE TABLE library_covers (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  digest TEXT NOT NULL,
  bytes BLOB NOT NULL,
  size INTEGER NOT NULL CHECK(size BETWEEN 1 AND 1048576),
  created_at INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY(user_id, digest)
);
CREATE TRIGGER library_covers_quota BEFORE INSERT ON library_covers
WHEN NOT EXISTS (SELECT 1 FROM library_covers WHERE user_id=NEW.user_id AND digest=NEW.digest)
AND (SELECT COALESCE(SUM(size),0) FROM library_covers WHERE user_id=NEW.user_id) + NEW.size > 20971520
BEGIN SELECT RAISE(ABORT, 'cover_quota'); END;
