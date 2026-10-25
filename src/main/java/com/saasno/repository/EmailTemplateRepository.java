package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.EmailTemplateEntity;

import jakarta.persistence.EntityManager;

@Repository
public class EmailTemplateRepository extends JpaRepository<EmailTemplateEntity, String> {

	public EmailTemplateRepository() {
		super(EmailTemplateEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

}
