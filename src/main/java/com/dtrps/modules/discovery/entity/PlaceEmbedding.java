package com.dtrps.modules.discovery.entity;

import com.dtrps.modules.poi.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "place_embeddings")
@Getter
@Setter
public class PlaceEmbedding {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "embedding_id")
  private Long embeddingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false, unique = true)
  private Place place;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = 1536)
  @Column(name = "embedding", nullable = false)
  private float[] embedding;

  @Column(name = "source_text", nullable = false, columnDefinition = "text")
  private String sourceText;

  @Column(name = "source_hash", length = 64)
  private String sourceHash;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
