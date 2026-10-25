package com.saasno.utility.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.saasno.entity.AnnouncementEntity;
import com.saasno.utility.dto.announcement.AnnouncementDTO;

@Mapper(componentModel = "spring")
public interface AnnouncementMapper {

    AnnouncementDTO dto(AnnouncementEntity entity);

    List<AnnouncementDTO> dto(List<AnnouncementEntity> entities);
}
