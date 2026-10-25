package com.saasno.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ApiTokenEntity;

import jakarta.persistence.EntityManager;

@Repository
public class ApiTokenRepository extends JpaRepository<ApiTokenEntity, String> {

	public ApiTokenRepository() {
		super(ApiTokenEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public ApiTokenEntity findByHash(String hash) {
		return entityManager.createQuery("SELECT t FROM ApiTokenEntity t WHERE t.tokenHash = :hash", ApiTokenEntity.class)
				.setParameter("hash", hash).getResultStream().findFirst().orElse(null);
	}

	// The tokens of one user that can still be used, newest first.
	public List<ApiTokenEntity> usable(String userId, LocalDateTime now) {
		return entityManager.createQuery("SELECT t FROM ApiTokenEntity t WHERE t.userId = :id AND t.revokedAt IS NULL AND t.expiresAt > :now "
				+ "ORDER BY t.registered DESC", ApiTokenEntity.class).setParameter("id", userId).setParameter("now", now).getResultList();
	}

}
