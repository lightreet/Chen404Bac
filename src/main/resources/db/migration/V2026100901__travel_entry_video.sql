ALTER TABLE `travel_memory_entry`
  ADD COLUMN `video_url` varchar(500) DEFAULT NULL COMMENT '短视频地址；为空时为图片，image_url 保留封面图' AFTER `image_url`;
