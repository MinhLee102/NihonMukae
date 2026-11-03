-- N3 có 内容理解(長文) (問題6, 4 câu), không có 主張理解. Dạng reading_long áp dụng cho N3 và N1,
-- reading_argument (主張理解) chỉ từ N2 trở lên.
UPDATE question_types SET levels = '{3,1}' WHERE code = 'reading_long';
UPDATE question_types SET levels = '{2,1}' WHERE code = 'reading_argument';

UPDATE exam_blueprints
SET type_id = (SELECT id FROM question_types WHERE code = 'reading_long'),
    sort_order = (SELECT sort_order FROM question_types WHERE code = 'reading_long')
WHERE level = 3
  AND type_id = (SELECT id FROM question_types WHERE code = 'reading_argument');
