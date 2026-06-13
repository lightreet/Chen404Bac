UPDATE sys_menu
SET icon = CASE icon
  WHEN 'Setting' THEN 'settings'
  WHEN 'Article' THEN 'article'
  WHEN 'Banner' THEN 'image'
  WHEN 'Category' THEN 'category'
  WHEN 'Comment' THEN 'comment'
  WHEN 'Document' THEN 'article'
  WHEN 'File' THEN 'file'
  WHEN 'Link' THEN 'link'
  WHEN 'Log' THEN 'files'
  WHEN 'Menu' THEN 'list'
  WHEN 'Role' THEN 'users'
  WHEN 'Site' THEN 'home'
  WHEN 'Tag' THEN 'tag'
  WHEN 'User' THEN 'user'
  ELSE icon
END
WHERE icon IN (
  'Setting',
  'Article',
  'Banner',
  'Category',
  'Comment',
  'Document',
  'File',
  'Link',
  'Log',
  'Menu',
  'Role',
  'Site',
  'Tag',
  'User'
);
