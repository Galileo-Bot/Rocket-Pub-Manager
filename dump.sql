create table banned_guilds
(
	name        varchar(100) null,
	id          varchar(20)  null,
	reason      text         not null,
	bannedSince datetime     null,
	constraint banned_guilds_id_uindex
		unique (id),
	constraint banned_guilds_name_uindex
		unique (name)
)
	comment 'Les serveurs bannis de Rocket Pub.' engine = InnoDB;

create table sanctions
(
	reason       text        not null,
	memberID     varchar(20) not null,
	appliedByID  varchar(20) null,
	durationMS   bigint      null,
	type         varchar(16) not null,
	id           int auto_increment
		primary key,
	sanctionedAt datetime    not null
)
	engine = InnoDB;

create table verifications
(
	staffID    varchar(20) not null,
	verifiedAt datetime    not null,
	messageID  varchar(20) null
)
	engine = InnoDB;

create index verifications_verifiedAt_index on verifications (verifiedAt);

create table ad_events
(
	id        int auto_increment primary key,
	authorID  varchar(20) not null,
	channelID varchar(20) not null,
	messageID varchar(20) not null,
	postedAt  datetime    not null
)
	engine = InnoDB;

create index ad_events_postedAt_index on ad_events (postedAt);
