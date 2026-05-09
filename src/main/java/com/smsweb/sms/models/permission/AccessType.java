package com.smsweb.sms.models.permission;

public enum AccessType {
    NOTHING, VIEW, CREATE, EDIT, DELETE, ALL;

    public boolean canView()   { return this == VIEW || this == ALL; }
    public boolean canCreate() { return this == CREATE || this == ALL; }
    public boolean canEdit()   { return this == EDIT || this == ALL; }
    public boolean canDelete() { return this == DELETE || this == ALL; }
}
