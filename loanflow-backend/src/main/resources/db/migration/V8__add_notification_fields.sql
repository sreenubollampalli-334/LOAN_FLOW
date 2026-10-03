ALTER TABLE notifications
ADD COLUMN resource_type VARCHAR(100) NULL,
ADD COLUMN resource_id BIGINT NULL,
ADD COLUMN metadata JSON NULL;

CREATE INDEX idx_notif_recipient_created ON notifications(recipient_id, created_at);
CREATE INDEX idx_notif_recipient_status ON notifications(recipient_id, status);
