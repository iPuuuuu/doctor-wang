-- V11: 聊天记录区分会话类型（general=AI 问诊 / anxiety=安心话聊），避免两种聊天互相串扰。
alter table chat_message add column conversation varchar(32) not null default 'general';
create index if not exists idx_chat_message_user_conversation on chat_message(user_id, conversation, created_at);
