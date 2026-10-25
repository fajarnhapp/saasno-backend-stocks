package com.saasno.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.UserEntity;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.model.data.DataTable;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

@Repository
public class UserRepository extends JpaRepository<UserEntity, String> {

	public UserRepository() {
		super(UserEntity.class);
	}

	@Autowired
	private EntityManager entityManager;
	private DataTable<UserEntity> dataTable;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	@PostConstruct
	public void init() {
		dataTable = new DataTable<UserEntity>(entityManager);
	}

	public DataResult<UserEntity> dataTable(DataFilter dataFilters) {
		return dataTable.getData(UserEntity.class, dataFilters);
	}

	public UserEntity findByUsername(String username) {
		TypedQuery<UserEntity> query = entityManager.createNamedQuery("UserEntity.findByUsername", UserEntity.class);
		query.setParameter("username", username);
		try {
			return query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		} catch (Exception e) {
			Log.error("Error findByUsername : ", e);
			return null;
		}
	}

	// Sign-in identifier: the username, or else the primary email address of the person (case-insensitive).
	public UserEntity findByLogin(String login) {
		UserEntity byUsername = findByUsername(login);
		if (byUsername != null) {
			// A user in the trash cannot sign in (nor receive a reset link).
			return byUsername.getDeletedAt() == null ? byUsername : null;
		}
		try {
			return entityManager.createQuery("SELECT u FROM UserEntity u JOIN u.person p JOIN p.contacts c "
					+ "WHERE c.type = com.saasno.utility.picker.ContactType.EMAIL AND c.primary = true AND LOWER(c.value) = :email AND u.deletedAt IS NULL",
					UserEntity.class).setParameter("email", login.trim().toLowerCase()).getResultStream().findFirst().orElse(null);
		} catch (Exception e) {
			Log.error("Error findByLogin : ", e);
			return null;
		}
	}

	// True when any person already uses this email address, other than the given person (may be null).
	public boolean emailTaken(String email, String exceptPersonId) {
		Long count = entityManager.createQuery("SELECT COUNT(c) FROM ContactEntity c WHERE c.type = com.saasno.utility.picker.ContactType.EMAIL "
				+ "AND LOWER(c.value) = :email AND (:person IS NULL OR c.person.id <> :person)", Long.class)
				.setParameter("email", email.toLowerCase()).setParameter("person", exceptPersonId).getSingleResult();
		return count > 0;
	}

	// Notifications and the counter of a deleted user are cleaned up too (no FK to the user table).
	public void deleteNotifications(String userId) {
		entityManager.createNativeQuery("DELETE FROM public.notification WHERE user_id = :id").setParameter("id", userId).executeUpdate();
		entityManager.createNativeQuery("DELETE FROM public.notification_unread WHERE user_id = :id").setParameter("id", userId).executeUpdate();
	}

	// The full name of the person behind a user, or null when there is none (used to label the audit log).
	public String fullNameOf(String userId) {
		return entityManager.createQuery("SELECT p.fullName FROM UserEntity u JOIN u.person p WHERE u.id = :id", String.class)
				.setParameter("id", userId).setMaxResults(1).getResultStream().findFirst().orElse(null);
	}

}
