package workflow.core.engine.domain.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository: Tenant Metadata */
@Repository
public interface TenantMetadataRepository extends JpaRepository<TenantMetadataEntity, Long> {

  Optional<TenantMetadataEntity> findByTenantId(String tenantId);

  boolean existsByTenantId(String tenantId);
}
