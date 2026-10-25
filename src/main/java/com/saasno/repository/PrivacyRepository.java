package com.saasno.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.DeletionRequestEntity;
import com.saasno.entity.PrivacyConsentEntity;
import com.saasno.entity.PrivacyPolicyEntity;

import jakarta.persistence.EntityManager;

// The privacy policy versions, who accepted which version, and the account deletion requests.
@Repository
public class PrivacyRepository {

	@Autowired
	private EntityManager entityManager;

	public EntityManager getEntityManager() {
		return entityManager;
	}

	// The newest published version, or null when none was published yet (the built-in text is in use).
	public PrivacyPolicyEntity latestPolicy() {
		return entityManager.createQuery("SELECT p FROM PrivacyPolicyEntity p ORDER BY p.version DESC", PrivacyPolicyEntity.class)
				.setMaxResults(1).getResultStream().findFirst().orElse(null);
	}

	public List<PrivacyPolicyEntity> policies() {
		return entityManager.createQuery("SELECT p FROM PrivacyPolicyEntity p ORDER BY p.version DESC", PrivacyPolicyEntity.class).getResultList();
	}

	public void store(Object entity) {
		entityManager.persist(entity);
	}

	public PrivacyConsentEntity consent(String userId, int version) {
		return entityManager.createQuery("SELECT c FROM PrivacyConsentEntity c WHERE c.userId = :u AND c.version = :v", PrivacyConsentEntity.class)
				.setParameter("u", userId).setParameter("v", version).getResultStream().findFirst().orElse(null);
	}

	public List<PrivacyConsentEntity> consents(String userId) {
		return entityManager.createQuery("SELECT c FROM PrivacyConsentEntity c WHERE c.userId = :u ORDER BY c.version DESC", PrivacyConsentEntity.class)
				.setParameter("u", userId).getResultList();
	}

	public DeletionRequestEntity pendingRequest(String userId) {
		return entityManager.createQuery("SELECT r FROM DeletionRequestEntity r WHERE r.userId = :u AND r.status = 'PENDING'", DeletionRequestEntity.class)
				.setParameter("u", userId).getResultStream().findFirst().orElse(null);
	}

	// Pending requests first, then the newest handled ones.
	public List<DeletionRequestEntity> requests() {
		return entityManager.createQuery("SELECT r FROM DeletionRequestEntity r ORDER BY CASE WHEN r.status = 'PENDING' THEN 0 ELSE 1 END, r.requestedAt DESC",
				DeletionRequestEntity.class).setMaxResults(100).getResultList();
	}

	public DeletionRequestEntity request(String id) {
		return entityManager.find(DeletionRequestEntity.class, id);
	}

	// Ids of the users who hold the given role (for notifications to the admins).
	@SuppressWarnings("unchecked")
	public List<String> userIdsWithRole(String roleId) {
		return entityManager.createNativeQuery("SELECT u.id FROM public.\"user\" u JOIN public.user_role ur ON ur.user_id = u.id "
				+ "WHERE ur.role_id = :r AND u.deleted_at IS NULL").setParameter("r", roleId).getResultList();
	}
}
