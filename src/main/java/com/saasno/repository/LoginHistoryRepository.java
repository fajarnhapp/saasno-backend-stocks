package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.LoginHistoryEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class LoginHistoryRepository extends JpaRepository<LoginHistoryEntity, String> {

	public LoginHistoryRepository() {
		super(LoginHistoryEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<LoginHistoryEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<LoginHistoryEntity>(entityManager);
	}

	public DataResult<LoginHistoryEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(LoginHistoryEntity.class, dataFilters);
	}

	// The latest attempts of one account, newest first.
	public java.util.List<LoginHistoryEntity> latest(String userId, int limit) {
		return entityManager.createQuery("SELECT h FROM LoginHistoryEntity h WHERE h.userId = :id ORDER BY h.registered DESC", LoginHistoryEntity.class)
				.setParameter("id", userId).setMaxResults(limit).getResultList();
	}

	public long deleteOlderThan(java.time.LocalDateTime cutoff) {
		return entityManager.createQuery("DELETE FROM LoginHistoryEntity h WHERE h.registered <= :cutoff").setParameter("cutoff", cutoff).executeUpdate();
	}

}
