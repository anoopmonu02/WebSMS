-- Only for a database where student_exit already exists (created before the sibling-group change).
-- A fresh database gets the column from create_tc_tables.sql. With spring.jpa.hibernate.ddl-auto=update the
-- column is added automatically on start; run this by hand only where ddl-auto is validate or none.
-- MySQL 5.7 has no ADD COLUMN IF NOT EXISTS: run it once; an error "Duplicate column name" means it is already there.
ALTER TABLE student_exit ADD COLUMN sibling_action VARCHAR(30) NULL AFTER login_disabled_by_exit;
