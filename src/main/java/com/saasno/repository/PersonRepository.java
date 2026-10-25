package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.PersonEntity;

import jakarta.persistence.EntityManager;

@Repository
public class PersonRepository extends JpaRepository<PersonEntity, String> {

	public PersonRepository() {
		super(PersonEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	// Sends pending changes to the database now (used between two steps that would otherwise clash on a unique index).
	public void flush() {
		entityManager.flush();
	}

}
