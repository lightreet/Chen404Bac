CREATE TABLE article (
    id BIGINT PRIMARY KEY, title VARCHAR(100), summary VARCHAR(500),
    content CLOB, content_html CLOB, cover_image VARCHAR(255), cover_file_id BIGINT,
    author_id BIGINT, category_id BIGINT, status INT, view_count INT, like_count INT, comment_count INT,
    is_top INT DEFAULT 0, is_recommend INT, is_original INT, original_url VARCHAR(255), password VARCHAR(255),
    visibility INT, comment_policy INT, publish_time TIMESTAMP, create_time TIMESTAMP,
    update_time TIMESTAMP, deleted INT DEFAULT 0
);
