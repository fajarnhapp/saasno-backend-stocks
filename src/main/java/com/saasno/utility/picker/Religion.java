package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum Religion {
    ISLAM,
    CHRISTIAN,
    CATHOLIC,
    HINDU,
    BUDDHIST,
    CONFUCIAN,
    OTHER;

    public static Religion fromString(String value) {
        for (Religion religion : Religion.values()) {
            if (religion.name().equalsIgnoreCase(value)) {
                return religion;
            }
        }
        throw new ApiException("400", "Religion must be one of ISLAM, CHRISTIAN, CATHOLIC, HINDU, BUDDHIST, CONFUCIAN, OTHER");
    }
}
