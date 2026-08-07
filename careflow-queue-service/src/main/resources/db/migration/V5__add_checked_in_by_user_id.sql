-- Migration V5 restore for Queue Service
ALTER TABLE queue.queue_entries ADD COLUMN IF NOT EXISTS checked_in_by_user_id UUID;
