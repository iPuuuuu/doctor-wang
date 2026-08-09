insert into app_user (user_id, account, password, name, age, height, weight)
values ('u1001', 'demo@doctorwang.app', '123456', '王先生', 25, 170, 73.00);

insert into health_profile_disease (user_id, disease_name)
select id, disease_name
from app_user
cross join (
    values
        ('轻度脂肪肝'),
        ('胃炎')
) as diseases(disease_name)
where account = 'demo@doctorwang.app';

insert into health_profile_record (user_id, record_date, record_type)
select id, '2026-06', '腹部超声'
from app_user
where account = 'demo@doctorwang.app';

insert into medical_report (id, user_id, title, report_date, report_type, summary, status)
select 10001, id, '腹部超声报告', '2026-06-17', '腹部超声', 'AI 初步判断存在轻度脂肪肝倾向，建议结合肝功能结果进一步评估。', 'completed'
from app_user
where account = 'demo@doctorwang.app';

select setval('medical_report_id_seq', greatest((select max(id) from medical_report), 1));

insert into medical_report_finding (report_id, finding_text)
values
    (10001, '轻度脂肪肝'),
    (10001, '建议结合肝功能结果复查');

insert into medical_report_suggestion (report_id, suggestion_text)
values
    (10001, '避免油腻饮食'),
    (10001, '控制体重'),
    (10001, '1-3 个月内复查');

insert into chat_message (user_id, role, content)
select id, 'user', '最近胃有点不舒服'
from app_user
where account = 'demo@doctorwang.app';

insert into chat_message (user_id, role, content)
select id, 'assistant', '根据你的历史记录，你之前有胃炎记录。建议近期保持清淡饮食，避免辛辣刺激。'
from app_user
where account = 'demo@doctorwang.app';
