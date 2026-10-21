ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS primary_for_booking BOOLEAN;
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_room_booking_doctor ON doctor_profiles(assigned_room_id)
WHERE is_active = true AND primary_for_booking = true AND assigned_room_id IS NOT NULL;
-- Preserve the existing unambiguous room assignment before adding handover colleagues.
UPDATE doctor_profiles d SET primary_for_booking = true
WHERE d.is_active = true AND d.assigned_room_id IS NOT NULL AND d.primary_for_booking IS NULL
AND (SELECT COUNT(*) FROM doctor_profiles other WHERE other.assigned_room_id=d.assigned_room_id AND other.is_active=true)=1;
