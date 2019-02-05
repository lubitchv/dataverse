ALTER TABLE datasetversion ADD COLUMN archivalcopylocation text;
ALTER TABLE externaltool ADD COLUMN contenttype text NOT NULL default 'text/tab-separated-values';

ALTER TABLE datavariable 
DROP COLUMN  if exists universe,
DROP COLUMN  if exists weighted;

CREATE SEQUENCE variablemetadata_id_seq
  INCREMENT 1
  MINVALUE 1
  START 1
  CACHE 1;

CREATE TABLE variablemetadata
(
  id bigint NOT NULL DEFAULT nextval('variablemetadata_id_seq'::regclass),
  label text,
  literalquestion text,
  interviewinstruction text,
  universe character varying(255),
  notes text,
  isweightvar boolean DEFAULT false,
  weightvariable_id bigint DEFAULT NULL,
  datavariable_id bigint NOT NULL,
  filemetadata_id bigint NOT NULL,
  CONSTRAINT variablemetadata_pkey PRIMARY KEY (id),
  CONSTRAINT fk_variablemetadata_datavariable_id FOREIGN KEY (datavariable_id)
      REFERENCES datavariable (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_variablemetadata_filemetadata_id FOREIGN KEY (filemetadata_id)
      REFERENCES filemetadata (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_variablemetadata_weight_datavariable_id FOREIGN KEY (weightvariable_id)
      REFERENCES datavariable (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE INDEX index_variablemetadata_datavariable_id
  ON variablemetadata
  USING btree
  (datavariable_id);

CREATE INDEX index_variablemetadata_filematadata_id
  ON variablemetadata
  USING btree
  (filemetadata_id);

CREATE UNIQUE INDEX index_variablemetadata_datavariable_filemetadata_id
  ON variablemetadata
  USING btree
  (datavariable_id,filemetadata_id);

CREATE SEQUENCE vargroup_id_seq
  INCREMENT 1
  MINVALUE 1
  START 1
  CACHE 1;

CREATE TABLE vargroup
(
    id bigint NOT NULL DEFAULT nextval('vargroup_id_seq'::regclass),
    label text,
    filemetadata_id bigint NOT NULL,
    CONSTRAINT vargroup_pkey PRIMARY KEY (id),
    CONSTRAINT fk_vargroup_filemetadata_id FOREIGN KEY (filemetadata_id)
      REFERENCES filemetadata (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE vargrouprel
(
    id bigint NOT NULL,
    datavariable_id bigint NOT NULL
);
