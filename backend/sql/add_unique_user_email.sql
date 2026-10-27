-- Preflight: this query must return no rows before the index is created.
select lower(trim(email)) as normalized_email, count(*) as user_count
from app_user
where email is not null and trim(email) <> ''
group by lower(trim(email))
having count(*) > 1;

-- Empty email addresses are treated as NULL, so accounts without email remain allowed.
alter table app_user add unique key uk_app_user_email ((nullif(lower(trim(email)), '')));
