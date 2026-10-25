package com.saasno.utility.dto.user;

// version is the file's last change time (0 = no picture); the client puts it in the URL so a new picture is not served from cache.
public record AvatarDTO(long version) {
}
