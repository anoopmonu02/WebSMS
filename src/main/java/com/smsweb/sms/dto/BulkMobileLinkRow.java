package com.smsweb.sms.dto;

import lombok.Getter;

import java.util.UUID;

/**
 * Read-only snapshot used after a Bulk Update "Mobile1, Mobile2" save: one enrollment's
 * saved mobile1 and the family account it is currently linked to (nullable).
 * Built by a JPQL constructor expression in FamilyMappingQueryRepository.findLinkRows().
 */
@Getter
public class BulkMobileLinkRow {

    private final UUID uuid;
    private final String mobile1;
    private final String familyMobile;
    private final String familyStatus;

    public BulkMobileLinkRow(UUID uuid, String mobile1, String familyMobile, String familyStatus) {
        this.uuid = uuid;
        this.mobile1 = mobile1;
        this.familyMobile = familyMobile;
        this.familyStatus = familyStatus;
    }
}
