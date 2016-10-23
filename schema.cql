CREATE KEYSPACE IF NOT EXISTS todos WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
CREATE TABLE IF NOT EXISTS todos.todos(user_id text, id bigint, name text, is_completed boolean, primary key(user_id, id));
