package com.saasno.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.AnnouncementEntity;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

@Repository
public class AnnouncementRepository extends JpaRepository<AnnouncementEntity, String> {

	public AnnouncementRepository() {
		super(AnnouncementEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<AnnouncementEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<AnnouncementEntity>(entityManager);
	}

	public DataResult<AnnouncementEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(AnnouncementEntity.class, dataFilters);
	}

	// Announcements that are switched on and inside their time window right now, most urgent first.
	public List<AnnouncementEntity> showing(LocalDateTime now) {
		return entityManager.createQuery("SELECT a FROM AnnouncementEntity a WHERE a.active = true "
				+ "AND (a.startsAt IS NULL OR a.startsAt <= :now) AND (a.endsAt IS NULL OR a.endsAt >= :now) ORDER BY a.registered DESC",
				AnnouncementEntity.class).setParameter("now", now).getResultList();
	}

}
