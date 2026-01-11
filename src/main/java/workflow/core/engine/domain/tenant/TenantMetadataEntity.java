package workflow.core.engine.domain.tenant;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Domain Entity: Tenant Metadata (v2) Represents a tenant in the multi-tenant workflow system */
@Entity
@Table(name = "tenant_metadata")
@Data
@NoArgsConstructor
public class TenantMetadataEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, unique = true, length = 100)
  private String tenantId;

  @Column(name = "tenant_name", nullable = false, length = 500)
  private String tenantName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private TenantStatus status = TenantStatus.ACTIVE;

  @Column(name = "config_json", columnDefinition = "TEXT")
  private String configJson;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  public TenantMetadataEntity(String tenantId, String tenantName) {
    this.tenantId = tenantId;
    this.tenantName = tenantName;
    this.status = TenantStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    INACTIVE
  }
}
