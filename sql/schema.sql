DROP TABLE IF EXISTS key_gen;
DROP TABLE IF EXISTS workspaces;
DROP TABLE IF EXISTS constraints;

CREATE TABLE key_gen (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
);

CREATE TABLE workspaces (
  `pk` int(11) NOT NULL AUTO_INCREMENT,
  `workspace_id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  `bin` blob NOT NULL,
  PRIMARY KEY (`pk`)
);

CREATE UNIQUE INDEX idx_workspace_version ON workspaces (`workspace_id`, `version`);

CREATE TABLE constraints (
  `workspace_id` int(11),
  `remote_ref` varchar(32),
  UNIQUE KEY `remote_ref` (`remote_ref`)
);
