package com.smsweb.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Summary cards for the Mobile Users admin screen — answers "how many parents
 * are actually using the app" using existing FamilyAccount/MobileRefreshToken
 * data only (no new tracking).
 */
@Getter
@AllArgsConstructor
public class MobileUserStatsDto {
    private long totalFamilies;
    private long everLoggedIn;
    private long activeLast30Days;
    private long validSessionNow;
}
