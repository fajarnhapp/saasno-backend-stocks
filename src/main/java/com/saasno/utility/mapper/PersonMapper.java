package com.saasno.utility.mapper;

import org.mapstruct.Mapper;

import com.saasno.entity.ContactEntity;
import com.saasno.entity.PersonEntity;
import com.saasno.utility.dto.person.ContactDTO;
import com.saasno.utility.dto.person.PersonDTO;

@Mapper(componentModel = "spring")
public interface PersonMapper {

    PersonDTO dto(PersonEntity entity);

    ContactDTO dto(ContactEntity entity);
}
