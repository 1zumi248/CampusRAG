-- 文档表
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255),
    file_name VARCHAR(500),
    file_type VARCHAR(255),
    file_size BIGINT,
    content TEXT,
    content_hash VARCHAR(64),  -- 用于检测更新
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档分片表（带向量字段）
CREATE TABLE document_chunks (
    id SERIAL PRIMARY KEY,
    document_id INTEGER REFERENCES documents(id),
    chunk_index INTEGER,
    content TEXT,
    metadata JSONB,  -- 存储元数据如标题、位置等
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引由 PgVectorEmbeddingStore 在 embeddings 表上自动管理

-- 会话表
CREATE TABLE conversations (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) DEFAULT '新建会话',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户可见消息表（供前端渲染历史）
CREATE TABLE messages (
    id SERIAL PRIMARY KEY,
    conversation_id INTEGER REFERENCES conversations(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer TEXT,
    sources JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- LangChain4j ChatMemory 持久化表
CREATE TABLE chat_memory (
    memory_id VARCHAR(64) PRIMARY KEY,
    messages JSONB NOT NULL
);