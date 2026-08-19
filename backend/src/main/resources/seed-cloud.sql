-- Seed data for cloud (Aiven defaultdb)

INSERT IGNORE INTO room_config (config_key, config_value) VALUES
('max_rooms_per_floor', '10'),
('max_members_per_room', '6'),
('wifi_allowed_subnets', '192.168.0.0/16,10.0.0.0/8,172.16.0.0/12'),
('hostel_latitude', '12.8231'),
('hostel_longitude', '80.0444'),
('hostel_radius', '50');

INSERT IGNORE INTO buildings (id, name, type) VALUES (1, 'Building A', 'NRI'), (2, 'Building B', 'NORMAL');

INSERT IGNORE INTO rooms (building_id, floor_number, room_number, max_members) VALUES
(1, 1, '101', 6), (1, 1, '102', 6), (1, 1, '103', 6), (1, 1, '104', 6), (1, 1, '105', 6),
(1, 1, '106', 6), (1, 1, '107', 6), (1, 1, '108', 6), (1, 1, '109', 6), (1, 1, '110', 6),
(1, 2, '201', 6), (1, 2, '202', 6), (1, 2, '203', 6), (1, 2, '204', 6), (1, 2, '205', 6),
(1, 2, '206', 6), (1, 2, '207', 6), (1, 2, '208', 6), (1, 2, '209', 6), (1, 2, '210', 6),
(1, 3, '301', 6), (1, 3, '302', 6), (1, 3, '303', 6), (1, 3, '304', 6), (1, 3, '305', 6),
(1, 3, '306', 6), (1, 3, '307', 6), (1, 3, '308', 6), (1, 3, '309', 6), (1, 3, '310', 6);

INSERT IGNORE INTO rooms (building_id, floor_number, room_number, max_members) VALUES
(2, 1, '101', 6), (2, 1, '102', 6), (2, 1, '103', 6), (2, 1, '104', 6), (2, 1, '105', 6),
(2, 1, '106', 6), (2, 1, '107', 6), (2, 1, '108', 6), (2, 1, '109', 6), (2, 1, '110', 6),
(2, 2, '201', 6), (2, 2, '202', 6), (2, 2, '203', 6), (2, 2, '204', 6), (2, 2, '205', 6),
(2, 2, '206', 6), (2, 2, '207', 6), (2, 2, '208', 6), (2, 2, '209', 6), (2, 2, '210', 6),
(2, 3, '301', 6), (2, 3, '302', 6), (2, 3, '303', 6), (2, 3, '304', 6), (2, 3, '305', 6),
(2, 3, '306', 6), (2, 3, '307', 6), (2, 3, '308', 6), (2, 3, '309', 6), (2, 3, '310', 6);

INSERT IGNORE INTO wardens (name, email, password_hash, hostel, phone) VALUES
('Ram', 'ram@mit.edu', '$2a$10$9Vw3Fdy7DyCXmAW7XG4dAO2lVc4Vayng/Xow2nAlMP4HTpzn/x9bW', 'NRI', '9876543210'),
('Rajesh', 'rajesh@mit.edu', '$2a$10$u039FG6i7pE.nEjJtiI3O.dGZy2KhT18q0VOfdfnb7Oon.Ob9OMtu', 'Marutham', '9876543211');

INSERT IGNORE INTO security_guards (name, email, password_hash, hostel, phone) VALUES
('muthu', 'muthu@mit.edu', '$2a$10$xhYbBcJFal8ildwL4OvvYOynu9RW/G7PunB9JiRmYagIM5s.x50G6', 'NRI', '9876543212'),
('somu', 'somu@mit.edu', '$2a$10$Rq8cGIg4XvhQBVw/3Ui2z.afZJvr6LchIzACM2sOwCHHMDIkuKQP6', 'Marutham', '9876543213');

INSERT IGNORE INTO students (name, email, password_hash, roll_no, department, hostel, room_number, contact_number, parent_number) VALUES
('Yuvi', 'yuvi@mit.edu', '$2a$10$H3Ag5JR2uJDmBLCLY1NOSeLsHKcNvMmBNQlYPMsZbSqBMGojXWhnK', '2024503541', 'CT', 'NRI', '101', '9876543214', '9876543215'),
('Aravinth', 'arvi@mit.edu', '$2a$10$oE5clQ8xgcRrCzvcUabJoeupjQiNHr7MBR.n2ZQD0F0uJABDoJAa.', '2024503001', 'CT', 'Marutham', '201', '9876543216', '9876543217');

SELECT 'Seed data loaded.' AS Status;
