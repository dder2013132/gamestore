-- Run in your DB client as the database operator, after 008_add_user_role.sql.
-- First register an ordinary account through the website, then replace CHANGE_ME below.
-- No password or password hash needs to be written in this file.
DO $$
DECLARE
    target_username TEXT := 'CHANGE_ME';
BEGIN
    IF target_username = 'CHANGE_ME' OR btrim(target_username) = '' THEN
        RAISE EXCEPTION 'Replace CHANGE_ME with an existing username before running this script.';
    END IF;
    UPDATE users SET role = 'ADMIN' WHERE username = lower(btrim(target_username));
    IF NOT FOUND THEN
        RAISE EXCEPTION 'The account does not exist. Register it on the website first.';
    END IF;
END $$;

SELECT user_id, username, role FROM users WHERE role = 'ADMIN' ORDER BY user_id;
