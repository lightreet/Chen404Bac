ALTER TABLE `travel_memory_location`
  ADD COLUMN `visibility` tinyint NOT NULL DEFAULT 2 COMMENT '可见性：0-公开 2-知友可见' AFTER `status`;

ALTER TABLE `travel_memory_location`
  ADD KEY `idx_travel_memory_location_visibility` (`status`, `visibility`, `sort_order`, `id`);
