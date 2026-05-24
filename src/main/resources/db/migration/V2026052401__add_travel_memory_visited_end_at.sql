ALTER TABLE `travel_memory_location`
  ADD COLUMN `visited_end_at` datetime DEFAULT NULL COMMENT '到访结束时间' AFTER `visited_at`,
  ADD KEY `idx_travel_memory_location_visited_end_at` (`visited_end_at`);
