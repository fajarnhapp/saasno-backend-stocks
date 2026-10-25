package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum Gender {
    MALE,
    FEMALE;

    public static Gender fromString(String value) {
        for (Gender gender : Gender.values()) {
            if (gender.name().equalsIgnoreCase(value)) {
                return gender;
            }
        }
        throw new ApiException("400", "Gender must be MALE or FEMALE");
    }
}
