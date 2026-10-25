package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ChangeLogEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class ChangeLogRepository extends JpaRepository<ChangeLogEntity, String> {

	public ChangeLogRepository() {
		super(ChangeLogEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<ChangeLogEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<ChangeLogEntity>(entityManager);
	}

	public DataResult<ChangeLogEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(ChangeLogEntity.class, dataFilters);
	}

	public long deleteOlderThan(java.time.LocalDateTime cutoff) {
		return entityManager.createQuery("DELETE FROM ChangeLogEntity c WHERE c.registered <= :cutoff").setParameter("cutoff", cutoff).executeUpdate();
	}

}
