-- SQLite schema, applied on every startup: every statement must stay idempotent.
--
-- Snowflakes are TEXT, durations are INTEGER milliseconds, and timestamps are TEXT in
-- 'YYYY-MM-DD HH:MM:SS' local time, the only format SQLite's DATE() can group on.
-- Bump user_version whenever a change here needs a data migration.
PRAGMA user_version = 1;

-- A guild is identified by its name, its snowflake, or both.
CREATE TABLE IF NOT EXISTS banned_guilds
(
	name        TEXT NULL UNIQUE,
	id          TEXT NULL UNIQUE,
	reason      TEXT NOT NULL,
	bannedSince TEXT NOT NULL,
	CHECK (name IS NOT NULL OR id IS NOT NULL)
) STRICT;

CREATE TABLE IF NOT EXISTS sanctions
(
	id           INTEGER PRIMARY KEY AUTOINCREMENT,
	memberID     TEXT    NOT NULL,
	appliedByID  TEXT    NULL,
	type         TEXT    NOT NULL,
	reason       TEXT    NOT NULL,
	durationMS   INTEGER NOT NULL DEFAULT 0,
	sanctionedAt TEXT    NOT NULL
) STRICT;

CREATE INDEX IF NOT EXISTS sanctions_memberID_index ON sanctions (memberID);

-- Ads approved by the staff.
CREATE TABLE IF NOT EXISTS verifications
(
	id         INTEGER PRIMARY KEY AUTOINCREMENT,
	staffID    TEXT NOT NULL,
	messageID  TEXT NULL,
	verifiedAt TEXT NOT NULL
) STRICT;

CREATE INDEX IF NOT EXISTS verifications_verifiedAt_index ON verifications (verifiedAt);
CREATE INDEX IF NOT EXISTS verifications_messageID_index ON verifications (messageID);

-- Ads posted in the ad channels, recorded for the stats commands.
CREATE TABLE IF NOT EXISTS ad_events
(
	id        INTEGER PRIMARY KEY AUTOINCREMENT,
	authorID  TEXT NOT NULL,
	channelID TEXT NOT NULL,
	messageID TEXT NOT NULL,
	postedAt  TEXT NOT NULL
) STRICT;

CREATE INDEX IF NOT EXISTS ad_events_postedAt_index ON ad_events (postedAt);
