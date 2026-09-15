-- =============================================================================
-- 种子数据 (演示账号密码均为 123456，BCrypt)
-- =============================================================================
USE campus_canteen;

-- BCrypt for 123456: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
INSERT INTO t_user (student_no, password, real_name, phone, role, department, grade, balance) VALUES
('2021001001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', '13800138001', 'STUDENT', '计算机学院', '2021', 200.00),
('T20210001',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李老师', '13900139001', 'STUDENT', '计算机学院', NULL, 500.00),
('S10001',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王师傅', '13700137001', 'STALL', '第一食堂', NULL, 0),
('A10001',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王管理员', '13600136001', 'ADMIN', '后勤处', NULL, 0),
('L10001',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '钱主管', '13500135001', 'ADMIN', '后勤处', NULL, 0),
('2021001002', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈四', '13800138002', 'STUDENT', '经济学院', '2022', 150.00),
('2021001003', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '刘五', '13800138003', 'STUDENT', '计算机学院', '2021', 180.00);

INSERT INTO t_health_profile (user_id, allergy_json, diet_taboo_json, target_calorie, target_protein, religion_tag) VALUES
(1, JSON_ARRAY('花生'), JSON_ARRAY('辛辣'), 2200, 80, NULL),
(2, JSON_ARRAY(), JSON_ARRAY(), 2000, 70, NULL),
(6, JSON_ARRAY('海鲜'), JSON_ARRAY(), 1800, 65, 'HALAL'),
(7, JSON_ARRAY(), JSON_ARRAY('猪肉'), 2100, 75, 'HALAL');

INSERT INTO t_canteen (name, location, open_time) VALUES
('第一食堂', '东区一号楼', '06:30-21:30'),
('第二食堂', '西区生活广场', '06:30-22:00');

INSERT INTO t_stall (canteen_id, name, type, queue_count) VALUES
(1, '主食窗口A', 'MAIN', 3),
(1, '川湘风味', 'FLAVOR', 5),
(1, '轻食吧', 'LIGHT', 1),
(1, '饮品站', 'DRINK', 2),
(2, '面食档', 'MAIN', 4),
(2, '清真风味', 'FLAVOR', 2);

INSERT INTO t_nutrition_tag (code, name, category, description) VALUES
('LOW_FAT', '低脂', 'NUTRITION', '脂肪含量较低'),
('HIGH_PROTEIN', '高蛋白', 'NUTRITION', '蛋白质丰富'),
('LOW_CALORIE', '低热量', 'NUTRITION', '热量控制'),
('LOW_SUGAR', '低糖', 'HEALTH', '控糖友好'),
('VEGETARIAN', '素食', 'RELIGION', '不含肉类'),
('HALAL', '清真', 'RELIGION', '清真认证'),
('ALLERGEN_FREE', '无过敏原', 'HEALTH', '常见过敏原少'),
('SPICY', '香辣', 'FLAVOR', '风味偏辣'),
('STAPLE', '主食', 'FLAVOR', '主食类'),
('SOUP', '汤品', 'FLAVOR', '汤类');

INSERT INTO t_dish (stall_id, category_id, name, price, stock, image_url, nutrition_json, meal_period, heat_score, status) VALUES
(1, 1, '红烧牛肉面', 15.00, 80, '/images/dish1.jpg', JSON_OBJECT('calorie',520,'protein',28,'fat',18,'carb',62), 'LUNCH', 96, 1),
(1, 1, '番茄鸡蛋盖饭', 12.00, 100, '/images/dish2.jpg', JSON_OBJECT('calorie',480,'protein',18,'fat',12,'carb',70), 'LUNCH', 88, 1),
(1, 1, '杂粮粥套餐', 8.00, 60, '/images/dish3.jpg', JSON_OBJECT('calorie',280,'protein',8,'fat',4,'carb',52), 'BREAKFAST', 70, 1),
(2, 2, '宫保鸡丁', 14.00, 50, '/images/dish4.jpg', JSON_OBJECT('calorie',450,'protein',26,'fat',22,'carb',28), 'LUNCH', 92, 1),
(2, 2, '麻婆豆腐', 10.00, 70, '/images/dish5.jpg', JSON_OBJECT('calorie',320,'protein',14,'fat',16,'carb',22), 'DINNER', 85, 1),
(3, 3, '鸡胸沙拉', 18.00, 40, '/images/dish6.jpg', JSON_OBJECT('calorie',260,'protein',32,'fat',8,'carb',12), 'LUNCH', 78, 1),
(3, 3, '藜麦轻食碗', 16.00, 35, '/images/dish7.jpg', JSON_OBJECT('calorie',300,'protein',12,'fat',9,'carb',42), 'LUNCH', 65, 1),
(4, 4, '鲜榨橙汁', 8.00, 90, '/images/dish8.jpg', JSON_OBJECT('calorie',110,'protein',1,'fat',0,'carb',26), 'LUNCH', 60, 1),
(5, 1, '牛肉拉面', 14.00, 75, '/images/dish9.jpg', JSON_OBJECT('calorie',500,'protein',24,'fat',15,'carb',68), 'DINNER', 90, 1),
(6, 2, '清真羊肉炒饭', 13.00, 55, '/images/dish10.jpg', JSON_OBJECT('calorie',540,'protein',22,'fat',16,'carb',72), 'LUNCH', 82, 1),
(6, 2, '清真清汤面', 11.00, 65, '/images/dish11.jpg', JSON_OBJECT('calorie',380,'protein',16,'fat',8,'carb',58), 'NIGHT', 55, 1),
(3, 3, '低脂鸡丝凉面', 12.00, 45, '/images/dish12.jpg', JSON_OBJECT('calorie',310,'protein',20,'fat',6,'carb',45), 'LUNCH', 72, 1);

-- 标签关联
INSERT INTO t_dish_tag (dish_id, tag_id) VALUES
(1, 2), (1, 9),
(2, 9),
(3, 3), (3, 4), (3, 5), (3, 9),
(4, 2), (4, 8),
(5, 5), (5, 8),
(6, 1), (6, 2), (6, 3), (6, 7),
(7, 1), (7, 3), (7, 5),
(8, 3), (8, 4),
(9, 2), (9, 9),
(10, 6), (10, 9),
(11, 6), (11, 9), (11, 10),
(12, 1), (12, 3), (12, 9);

-- 示例营养日数据
INSERT INTO t_nutrition_daily (user_id, stat_date, calorie, protein, fat, carb, protein_rate) VALUES
(1, CURDATE(), 1650, 62, 48, 210, 77.50),
(6, CURDATE(), 1420, 48, 40, 180, 73.85),
(7, CURDATE(), 1880, 70, 55, 240, 93.33);
