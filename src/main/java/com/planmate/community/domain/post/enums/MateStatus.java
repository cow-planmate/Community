package com.planmate.community.domain.post.enums;

import java.util.Locale;

public enum MateStatus {

    RECRUITING, CLOSED;

    public String toLowerValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
