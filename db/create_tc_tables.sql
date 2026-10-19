-- Transfer Certificate (TC) feature: tables
--
-- WHY:  Adds the 7 tables used by the TC / strike-off / due clearance feature (see
--       WebSMS_TC_Implementation_Plan.md section 5). Production may run with
--       spring.jpa.hibernate.ddl-auto=none or validate, so run this script on every
--       database BEFORE deploying the build that contains the TC entities.
-- WHAT: class_division, class_division_grade, student_exit, due_clearance_receipt, tc,
--       student_link, student_execution. Nothing existing is altered.
-- SAFE: Idempotent (CREATE TABLE IF NOT EXISTS), additive only. Compatible with MySQL 5.7
--       (utf8mb4 / utf8mb4_unicode_ci, TEXT instead of JSON, no CTE, no IF NOT EXISTS on indexes).
-- ROLLBACK: Turn the feature off (TC_FEATURE_ENABLED_<schoolId> = false or missing). No data in
--       existing tables is touched, so the new tables can stay unused.
--
-- Index and constraint names below must stay identical to the names in the entity classes
-- (com.smsweb.sms.models.tc) so that ddl-auto=update does not add duplicates.
-- Run in this order (foreign keys).

-- 1. Class groups and their TC register ---------------------------------------------------
CREATE TABLE IF NOT EXISTS class_division (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    uuid          CHAR(36)      NOT NULL,
    school_id     BIGINT        NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    description   VARCHAR(255)  NULL,
    tc_format     VARCHAR(30)   NOT NULL,
    start_number  BIGINT        NOT NULL DEFAULT 1,
    next_number   BIGINT        NOT NULL DEFAULT 1,
    status        VARCHAR(20)   NOT NULL DEFAULT 'Active',
    created_by    BIGINT        NOT NULL,
    creation_date DATETIME(6)   NULL,
    updated_by    BIGINT        NULL,
    last_updated  DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_division_uuid (uuid),
    UNIQUE KEY uk_class_division_school_name (school_id, name),
    CONSTRAINT fk_class_division_school     FOREIGN KEY (school_id)  REFERENCES school (id),
    CONSTRAINT fk_class_division_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_class_division_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Which grades belong to which group (a grade is in one group per school) ----------------
CREATE TABLE IF NOT EXISTS class_division_grade (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    school_id     BIGINT      NOT NULL,
    division_id   BIGINT      NOT NULL,
    grade_id      BIGINT      NOT NULL,
    created_by    BIGINT      NOT NULL,
    creation_date DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_division_grade_school_grade (school_id, grade_id),
    KEY idx_class_division_grade_division (division_id),
    CONSTRAINT fk_cdg_school     FOREIGN KEY (school_id)   REFERENCES school (id),
    CONSTRAINT fk_cdg_division   FOREIGN KEY (division_id) REFERENCES class_division (id),
    CONSTRAINT fk_cdg_grade      FOREIGN KEY (grade_id)    REFERENCES grade (id),
    CONSTRAINT fk_cdg_created_by FOREIGN KEY (created_by)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. One row per strike-off ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_exit (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    uuid                   CHAR(36)      NOT NULL,
    school_id              BIGINT        NOT NULL,
    academic_year_id       BIGINT        NOT NULL,
    student_id             BIGINT        NOT NULL,
    academic_student_id    BIGINT        NOT NULL,
    grade_id               BIGINT        NOT NULL,
    section_id             BIGINT        NOT NULL,
    medium_id              BIGINT        NOT NULL,
    exit_reason            VARCHAR(30)   NOT NULL,
    absent_days            INT           NULL,
    exit_date              DATE          NOT NULL,
    include_last_month     TINYINT(1)    NOT NULL DEFAULT 1,
    calc_amount            DECIMAL(10,2) NOT NULL DEFAULT 0,
    calc_breakdown         TEXT          NULL,
    dues_manual            TINYINT(1)    NOT NULL DEFAULT 0,
    dues_manual_reason     VARCHAR(255)  NULL,
    dues_frozen            TINYINT(1)    NOT NULL DEFAULT 0,
    frozen_amount          DECIMAL(10,2) NOT NULL DEFAULT 0,
    frozen_breakdown       TEXT          NULL,
    frozen_at              DATETIME(6)   NULL,
    login_disabled_by_exit TINYINT(1)    NOT NULL DEFAULT 0,
    sibling_action         VARCHAR(30)   NULL,
    status                 VARCHAR(20)   NOT NULL,
    remark                 VARCHAR(500)  NULL,
    approved_by            BIGINT        NULL,
    approved_at            DATETIME(6)   NULL,
    approval_remark        VARCHAR(500)  NULL,
    cancelled_by           BIGINT        NULL,
    cancelled_at           DATETIME(6)   NULL,
    cancel_reason          VARCHAR(500)  NULL,
    created_by             BIGINT        NOT NULL,
    creation_date          DATETIME(6)   NULL,
    updated_by             BIGINT        NULL,
    last_updated           DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_exit_uuid (uuid),
    KEY idx_student_exit_school_year_status (school_id, academic_year_id, status),
    KEY idx_student_exit_student (student_id),
    KEY idx_student_exit_acad_student (academic_student_id),
    CONSTRAINT fk_student_exit_school       FOREIGN KEY (school_id)           REFERENCES school (id),
    CONSTRAINT fk_student_exit_year         FOREIGN KEY (academic_year_id)    REFERENCES academic_year (id),
    CONSTRAINT fk_student_exit_student      FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_student_exit_acad_student FOREIGN KEY (academic_student_id) REFERENCES academic_students (id),
    CONSTRAINT fk_student_exit_grade        FOREIGN KEY (grade_id)            REFERENCES grade (id),
    CONSTRAINT fk_student_exit_section      FOREIGN KEY (section_id)          REFERENCES section (id),
    CONSTRAINT fk_student_exit_medium       FOREIGN KEY (medium_id)           REFERENCES medium (id),
    CONSTRAINT fk_student_exit_approved_by  FOREIGN KEY (approved_by)         REFERENCES users (id),
    CONSTRAINT fk_student_exit_cancelled_by FOREIGN KEY (cancelled_by)        REFERENCES users (id),
    CONSTRAINT fk_student_exit_created_by   FOREIGN KEY (created_by)          REFERENCES users (id),
    CONSTRAINT fk_student_exit_updated_by   FOREIGN KEY (updated_by)          REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Due Clearance Receipt -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS due_clearance_receipt (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)      NOT NULL,
    school_id         BIGINT        NOT NULL,
    exit_id           BIGINT        NOT NULL,
    receipt_seq       BIGINT        NOT NULL,
    receipt_no        VARCHAR(40)   NOT NULL,
    receipt_date      DATE          NOT NULL,
    mode              VARCHAR(15)   NOT NULL,
    fee_submission_id BIGINT        NULL,
    amount_paid       DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount_reason   VARCHAR(255)  NULL,
    payment_mode      VARCHAR(30)   NULL,
    reference_no      VARCHAR(100)  NULL,
    breakdown         TEXT          NULL,
    remark            VARCHAR(500)  NULL,
    submission_token  VARCHAR(64)   NULL,
    record_status     VARCHAR(10)   NOT NULL DEFAULT 'VALID',
    voided_by         BIGINT        NULL,
    voided_at         DATETIME(6)   NULL,
    void_reason       VARCHAR(255)  NULL,
    created_by        BIGINT        NOT NULL,
    creation_date     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dcr_uuid (uuid),
    UNIQUE KEY uk_dcr_school_seq (school_id, receipt_seq),
    UNIQUE KEY uk_dcr_fee_submission (fee_submission_id),
    UNIQUE KEY uk_dcr_submission_token (submission_token),
    KEY idx_dcr_exit (exit_id),
    CONSTRAINT fk_dcr_school         FOREIGN KEY (school_id)         REFERENCES school (id),
    CONSTRAINT fk_dcr_exit           FOREIGN KEY (exit_id)           REFERENCES student_exit (id),
    CONSTRAINT fk_dcr_fee_submission FOREIGN KEY (fee_submission_id) REFERENCES fee_submission (id),
    CONSTRAINT fk_dcr_voided_by      FOREIGN KEY (voided_by)         REFERENCES users (id),
    CONSTRAINT fk_dcr_created_by     FOREIGN KEY (created_by)        REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Transfer Certificates (every issue and re-issue) ---------------------------------------
CREATE TABLE IF NOT EXISTS tc (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                  CHAR(36)     NOT NULL,
    school_id             BIGINT       NOT NULL,
    exit_id               BIGINT       NOT NULL,
    student_id            BIGINT       NOT NULL,
    academic_student_id   BIGINT       NOT NULL,
    division_id           BIGINT       NOT NULL,
    tc_number             BIGINT       NOT NULL,
    tc_format             VARCHAR(30)  NOT NULL,
    issue_date            DATE         NOT NULL,
    collected_by_name     VARCHAR(150) NOT NULL,
    collected_by_relation VARCHAR(20)  NOT NULL,
    reissue_no            INT          NOT NULL DEFAULT 1,
    previous_tc_id        BIGINT       NULL,
    cause                 VARCHAR(500) NULL,
    status                VARCHAR(15)  NOT NULL DEFAULT 'VALID',
    tc_category           VARCHAR(50)  NULL,
    tc_data               TEXT         NULL,
    created_by            BIGINT       NOT NULL,
    creation_date         DATETIME(6)  NULL,
    updated_by            BIGINT       NULL,
    last_updated          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tc_uuid (uuid),
    UNIQUE KEY uk_tc_division_number (division_id, tc_number),
    KEY idx_tc_student (student_id),
    KEY idx_tc_exit (exit_id),
    CONSTRAINT fk_tc_school         FOREIGN KEY (school_id)           REFERENCES school (id),
    CONSTRAINT fk_tc_exit           FOREIGN KEY (exit_id)             REFERENCES student_exit (id),
    CONSTRAINT fk_tc_student        FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_tc_acad_student   FOREIGN KEY (academic_student_id) REFERENCES academic_students (id),
    CONSTRAINT fk_tc_division       FOREIGN KEY (division_id)         REFERENCES class_division (id),
    CONSTRAINT fk_tc_previous       FOREIGN KEY (previous_tc_id)      REFERENCES tc (id),
    CONSTRAINT fk_tc_created_by     FOREIGN KEY (created_by)          REFERENCES users (id),
    CONSTRAINT fk_tc_updated_by     FOREIGN KEY (updated_by)          REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Link between an old record and the new record of a new-session return -----------------
CREATE TABLE IF NOT EXISTS student_link (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                    CHAR(36)     NOT NULL,
    school_id               BIGINT       NOT NULL,
    old_student_id          BIGINT       NOT NULL,
    new_student_id          BIGINT       NOT NULL,
    old_academic_student_id BIGINT       NOT NULL,
    new_academic_student_id BIGINT       NOT NULL,
    link_type               VARCHAR(20)  NOT NULL,
    old_registration_no     VARCHAR(50)  NOT NULL,
    new_registration_no     VARCHAR(50)  NOT NULL,
    old_psrn                BIGINT       NULL,
    new_psrn                BIGINT       NULL,
    old_class_sr_no         VARCHAR(50)  NULL,
    tc_id                   BIGINT       NULL,
    admission_basis         VARCHAR(20)  NOT NULL,
    remark                  VARCHAR(500) NULL,
    created_by              BIGINT       NOT NULL,
    creation_date           DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_link_uuid (uuid),
    UNIQUE KEY uk_student_link_new_student (new_student_id),
    KEY idx_student_link_old_student (old_student_id),
    CONSTRAINT fk_student_link_school      FOREIGN KEY (school_id)               REFERENCES school (id),
    CONSTRAINT fk_student_link_old_student FOREIGN KEY (old_student_id)          REFERENCES students (id),
    CONSTRAINT fk_student_link_new_student FOREIGN KEY (new_student_id)          REFERENCES students (id),
    CONSTRAINT fk_student_link_old_acad    FOREIGN KEY (old_academic_student_id) REFERENCES academic_students (id),
    CONSTRAINT fk_student_link_new_acad    FOREIGN KEY (new_academic_student_id) REFERENCES academic_students (id),
    CONSTRAINT fk_student_link_tc          FOREIGN KEY (tc_id)                   REFERENCES tc (id),
    CONSTRAINT fk_student_link_created_by  FOREIGN KEY (created_by)              REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Append-only student timeline -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_execution (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                CHAR(36)     NOT NULL,
    school_id           BIGINT       NOT NULL,
    academic_year_id    BIGINT       NOT NULL,
    student_id          BIGINT       NOT NULL,
    academic_student_id BIGINT       NULL,
    event_type          VARCHAR(40)  NOT NULL,
    event_date          DATE         NOT NULL,
    recorded_at         DATETIME(6)  NOT NULL,
    exit_reason         VARCHAR(30)  NULL,
    grade_id            BIGINT       NULL,
    section_id          BIGINT       NULL,
    medium_id           BIGINT       NULL,
    old_value           VARCHAR(255) NULL,
    new_value           VARCHAR(255) NULL,
    remark              VARCHAR(500) NULL,
    ref_type            VARCHAR(30)  NULL,
    ref_id              BIGINT       NULL,
    source              VARCHAR(10)  NOT NULL DEFAULT 'WEB',
    record_status       VARCHAR(10)  NOT NULL DEFAULT 'VALID',
    voided_by           BIGINT       NULL,
    voided_at           DATETIME(6)  NULL,
    void_reason         VARCHAR(255) NULL,
    created_by          BIGINT       NULL,
    creation_date       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_execution_uuid (uuid),
    KEY idx_student_execution_student (student_id, event_date, id),
    KEY idx_student_execution_school_event (school_id, event_type, event_date),
    KEY idx_student_execution_ref (ref_type, ref_id),
    CONSTRAINT fk_student_execution_school      FOREIGN KEY (school_id)           REFERENCES school (id),
    CONSTRAINT fk_student_execution_year        FOREIGN KEY (academic_year_id)    REFERENCES academic_year (id),
    CONSTRAINT fk_student_execution_student     FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_student_execution_acad        FOREIGN KEY (academic_student_id) REFERENCES academic_students (id),
    CONSTRAINT fk_student_execution_grade       FOREIGN KEY (grade_id)            REFERENCES grade (id),
    CONSTRAINT fk_student_execution_section     FOREIGN KEY (section_id)          REFERENCES section (id),
    CONSTRAINT fk_student_execution_medium      FOREIGN KEY (medium_id)           REFERENCES medium (id),
    CONSTRAINT fk_student_execution_voided_by   FOREIGN KEY (voided_by)           REFERENCES users (id),
    CONSTRAINT fk_student_execution_created_by  FOREIGN KEY (created_by)          REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- OPTIONAL hardening (run by hand, MySQL 5.7.6+ only; Hibernate cannot create it and the entity
-- does not map it): allow only one open exit (EXITED or APPROVED) per student at database level.
-- ALTER TABLE student_exit
--   ADD COLUMN open_student_id BIGINT AS (IF(status IN ('EXITED','APPROVED'), student_id, NULL)) STORED,
--   ADD UNIQUE KEY uk_student_exit_open_student (open_student_id);
-- (If you add it, ddl-auto=update will leave it alone; it is not in the entity.)

-- Quick check after running: should list the 7 tables.
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('class_division','class_division_grade','student_exit','due_clearance_receipt',
                     'tc','student_link','student_execution')
ORDER BY table_name;
