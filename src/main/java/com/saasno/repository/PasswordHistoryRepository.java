package com.saasno.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.PasswordHistoryEntity;

import jakarta.persistence.EntityManager;

@Repository
public class PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, String> {

	public PasswordHistoryRepository() {
		super(PasswordHistoryEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	// The latest passwords of one account, newest first.
	public List<PasswordHistoryEntity> latest(String userId, int limit) {
		return entityManager.createQuery("SELECT h FROM PasswordHistoryEntity h WHERE h.userId = :id ORDER BY h.registered DESC", PasswordHistoryEntity.class)
				.setParameter("id", userId).setMaxResults(limit).getResultList();
	}

	// Keeps only the newest passwords of one account.
	public void keepLatest(String userId, int keep) {
		entityManager.createNativeQuery("DELETE FROM public.password_history WHERE user_id = :id AND id NOT IN "
				+ "(SELECT id FROM public.password_history WHERE user_id = :id ORDER BY registered DESC LIMIT :keep)")
				.setParameter("id", userId).setParameter("keep", keep).executeUpdate();
	}

}
