-- Переименовываем статус ACTIVE → APPROVED
UPDATE users SET status = 'APPROVED' WHERE status = 'ACTIVE';