-- 旧的 importing 记录没有租约，升级后会被周期恢复器重新认领。
ALTER TABLE reader_book ADD COLUMN import_token VARCHAR(64) NULL;
ALTER TABLE reader_book ADD COLUMN import_lease_until DATETIME NULL;
ALTER TABLE reader_book ADD COLUMN import_attempts INT NOT NULL DEFAULT 0;
CREATE INDEX idx_reader_book_import_recovery ON reader_book (deleted, status, import_lease_until, id);
