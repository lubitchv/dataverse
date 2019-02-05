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
  weightvariable_id bigint DEFAULT -1,
  datavariable_id bigint NOT NULL,
  filemetadata_id bigint NOT NULL,
  CONSTRAINT variablemetadata_pkey PRIMARY KEY (id),
  CONSTRAINT fk_variablemetadata_datavariable_id FOREIGN KEY (datavariable_id)
      REFERENCES datavariable (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_variablemetadata_filemetadata_id FOREIGN KEY (filemetadata_id)
      REFERENCES filemetadata (id) MATCH SIMPLE
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

CREATE INDEX index_variablemetadata_datavariable_filemetadata_id
  ON variablemetadata
  USING btree
  (datavariable_id,filemetadata_id);

ALTER TABLE public.variablemetadata
  OWNER TO dvnapp;
ALTER TABLE variablemetadata_id_seq
  OWNER TO dvnapp;
