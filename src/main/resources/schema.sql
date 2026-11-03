-- Runs before Hibernate creates tables, because the place_embeddings column needs this type.
CREATE EXTENSION IF NOT EXISTS vector;
