-- 昵称不允许重复。老数据里可能已经有重名的，先给「后来的人」加个后缀腾出位置，
-- 同一个昵称保留 id 最小的那个，否则唯一索引建不起来
UPDATE app_user u
    JOIN (SELECT nickname, keep_id
          FROM (SELECT nickname, MIN(id) AS keep_id
                FROM app_user
                GROUP BY nickname
                HAVING COUNT(*) > 1) t) d
    ON u.nickname = d.nickname AND u.id <> d.keep_id
SET u.nickname = CONCAT(LEFT(u.nickname, 40), '_', u.id);

ALTER TABLE app_user
    ADD UNIQUE KEY uk_app_user_nickname (nickname);
