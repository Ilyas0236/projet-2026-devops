-- auth_db est déjà créée automatiquement par POSTGRES_DB dans docker-compose.yml
-- On crée seulement les bases supplémentaires

CREATE DATABASE content_db;
CREATE DATABASE shop_db;
CREATE DATABASE payment_db;
CREATE DATABASE ticket_db;
CREATE DATABASE notification_db;
CREATE DATABASE sports_db;