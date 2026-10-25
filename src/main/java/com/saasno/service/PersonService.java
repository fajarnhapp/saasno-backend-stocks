package com.saasno.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.ContactEntity;
import com.saasno.entity.PersonEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.PersonRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.person.ContactDTO;
import com.saasno.utility.dto.person.PersonDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.mapper.PersonMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.picker.ContactLabel;
import com.saasno.utility.picker.ContactType;
import com.saasno.utility.picker.Gender;
import com.saasno.utility.picker.Religion;

import jakarta.transaction.Transactional;

// Biodata and contacts of a person. Used for the signed-in user's own profile and by the user admin screens.
@Service
public class PersonService {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern PHONE = Pattern.compile("^[+(]?[0-9][0-9 ()-]{4,29}$");
	private static final int MAX_CONTACTS = 10;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private PersonMapper personMapper;

	@Autowired
	private ChangeLogService changeLogService;

	@Transactional
	public ResponseModel<PersonDTO> me() {
		return new ResponseModel<PersonDTO>().success(personMapper.dto(currentPerson()));
	}

	@Transactional
	public ResponseModel<PersonDTO> updateMe(PersonDTO dto) {
		PersonEntity person = currentPerson();
		String before = summary(person);
		String contactsBefore = contactSummary(person);
		applyBiodata(person, dto);
		if (dto.getContacts() != null) {
			applyContacts(person, dto.getContacts());
		}
		personRepository.update(person);
		changeLogService.updated("PERSON", person.getId(), person.getFullName(), ChangeLogService.changes()
				.add("details", before, summary(person)).add("contacts", contactsBefore, contactSummary(person)));
		return new ResponseModel<PersonDTO>().success(personMapper.dto(person));
	}

	private static String summary(PersonEntity p) {
		return String.join(" | ", String.valueOf(p.getFullName()), String.valueOf(p.getNickname()), String.valueOf(p.getBirthPlace()),
				String.valueOf(p.getBirthDate()), String.valueOf(p.getGender()), String.valueOf(p.getReligion()));
	}

	private static String contactSummary(PersonEntity p) {
		return p.getContacts().stream().map(c -> c.getType() + " " + c.getValue() + (c.isPrimary() ? " (primary)" : ""))
				.sorted().collect(java.util.stream.Collectors.joining(", "));
	}

	private PersonEntity currentPerson() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null || user.getPerson() == null) {
			throw new ApiException("404", "User not found");
		}
		return user.getPerson();
	}

	// Full name is required; the others are optional and cleared when empty.
	public void applyBiodata(PersonEntity person, PersonDTO dto) {
		person.setFullName(requiredText(dto.getFullName(), "Full name", 128));
		person.setNickname(optionalText(dto.getNickname(), "Nickname", 64));
		person.setBirthPlace(optionalText(dto.getBirthPlace(), "Birth place", 64));
		LocalDate birthDate = dto.getBirthDate();
		if (birthDate != null && (birthDate.isAfter(LocalDate.now()) || birthDate.isBefore(LocalDate.of(1900, 1, 1)))) {
			throw new ApiException("400", "Birth date is not valid");
		}
		person.setBirthDate(birthDate);
		person.setGender(blank(dto.getGender()) ? null : Gender.fromString(dto.getGender().trim()));
		person.setReligion(blank(dto.getReligion()) ? null : Religion.fromString(dto.getReligion().trim()));
	}

	// Makes the contacts of the person equal to the given list: rows with a known id are updated, rows without are added and
	// stored rows that are not listed are removed. Each type (EMAIL, PHONE) has exactly one primary contact when it has any.
	public void applyContacts(PersonEntity person, List<ContactDTO> wanted) {
		if (wanted.size() > MAX_CONTACTS) {
			throw new ApiException("400", "A person can have at most " + MAX_CONTACTS + " contacts");
		}

		Map<String, ContactEntity> existing = new HashMap<>();
		for (ContactEntity contact : person.getContacts()) {
			existing.put(contact.getId(), contact);
		}

		// Validate everything first, so a bad entry changes nothing.
		Set<String> seen = new HashSet<>();
		Set<String> keep = new HashSet<>();
		Map<ContactType, Integer> primaries = new HashMap<>();
		List<ContactDTO> cleaned = new ArrayList<>();
		for (ContactDTO dto : wanted) {
			ContactType type = ContactType.fromString(dto.getType());
			String value = type == ContactType.EMAIL ? normalizeEmail(dto.getValue()) : normalizePhone(dto.getValue());
			if (!seen.add(type + ":" + value.toLowerCase(Locale.ROOT))) {
				throw new ApiException("400", "The contact " + value + " is listed twice");
			}
			if (dto.getId() != null && !dto.getId().isBlank()) {
				if (!existing.containsKey(dto.getId())) {
					throw new ApiException("404", "Contact does not exist");
				}
				keep.add(dto.getId());
			}
			if (type == ContactType.EMAIL && userRepository.emailTaken(value, person.getId())) {
				throw new ApiException("409", "The email " + value + " is already used by someone else");
			}
			if (dto.isPrimary()) {
				primaries.merge(type, 1, Integer::sum);
			}
			ContactDTO copy = new ContactDTO();
			copy.setId(dto.getId());
			copy.setType(type.name());
			copy.setValue(value);
			copy.setLabel(ContactLabel.fromString(dto.getLabel()).name());
			copy.setPrimary(dto.isPrimary());
			cleaned.add(copy);
		}
		for (Map.Entry<ContactType, Integer> entry : primaries.entrySet()) {
			if (entry.getValue() > 1) {
				throw new ApiException("400", "Only one " + entry.getKey().name().toLowerCase(Locale.ROOT) + " can be the primary one");
			}
		}

		// Step 1: remove what is gone and give up every primary flag, then write that out, so the unique indexes
		// (one primary per type, one address per person) never see two rows in conflict while the new values go in.
		person.getContacts().removeIf(contact -> !keep.contains(contact.getId()));
		person.getContacts().forEach(contact -> contact.setPrimary(false));
		personRepository.flush();

		// Step 2: apply the final values. The first contact of a type becomes its primary when none was chosen.
		Set<ContactType> hasPrimary = new HashSet<>(primaries.keySet());
		for (ContactDTO dto : cleaned) {
			ContactType type = ContactType.valueOf(dto.getType());
			boolean primary = dto.isPrimary();
			if (!primary && !hasPrimary.contains(type)) {
				primary = true;
				hasPrimary.add(type);
			}
			ContactEntity contact = dto.getId() == null || dto.getId().isBlank() ? null : existing.get(dto.getId());
			if (contact == null) {
				person.getContacts().add(new ContactEntity(person, type, dto.getValue(), ContactLabel.valueOf(dto.getLabel()), primary));
			} else {
				contact.setValue(dto.getValue());
				contact.setLabel(ContactLabel.valueOf(dto.getLabel()));
				contact.setPrimary(primary);
			}
		}
	}

	// Sets the primary email of a person (admin screens): the current primary email is changed, or one is added.
	public void setPrimaryEmail(PersonEntity person, String rawEmail) {
		String email = normalizeEmail(rawEmail);
		if (userRepository.emailTaken(email, person.getId())) {
			throw new ApiException("409", "The email " + email + " is already used by someone else");
		}
		for (ContactEntity contact : person.getContacts()) {
			if (contact.getType() == ContactType.EMAIL && contact.getValue().equalsIgnoreCase(email)) {
				if (!contact.isPrimary()) {
					person.getContacts().stream().filter(c -> c.getType() == ContactType.EMAIL).forEach(c -> c.setPrimary(false));
					personRepository.flush();
					contact.setPrimary(true);
				}
				return;
			}
		}
		for (ContactEntity contact : person.getContacts()) {
			if (contact.getType() == ContactType.EMAIL && contact.isPrimary()) {
				contact.setValue(email);
				return;
			}
		}
		person.getContacts().add(new ContactEntity(person, ContactType.EMAIL, email, ContactLabel.PERSONAL, true));
	}

	public static String normalizeEmail(String raw) {
		String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (email.isEmpty() || email.length() > 256 || !EMAIL.matcher(email).matches()) {
			throw new ApiException("400", "Email address is not valid");
		}
		return email;
	}

	public static String normalizePhone(String raw) {
		String phone = raw == null ? "" : raw.trim();
		if (!PHONE.matcher(phone).matches()) {
			throw new ApiException("400", "Phone number is not valid");
		}
		return phone;
	}

	private static String requiredText(String value, String name, int max) {
		String text = value == null ? "" : value.trim();
		if (text.isEmpty()) {
			throw new ApiException("400", name + " must not be empty");
		}
		if (text.length() > max) {
			throw new ApiException("400", name + " must be at most " + max + " characters");
		}
		return text;
	}

	private static String optionalText(String value, String name, int max) {
		return blank(value) ? null : requiredText(value, name, max);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
