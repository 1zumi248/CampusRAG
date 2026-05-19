package com.hznu.campusragbackend.common;

public final class Constants {

    private Constants() {}

    /** 文档解析最大字符数 */
    public static final int DOC_PARSE_MAX_CHARS = 500_000;

    /** 分块大小范围（字符数） */
    public static final int CHUNK_MIN_LENGTH = 300;
    public static final int CHUNK_MAX_LENGTH = 800;

    /** redis热点信息缓存前缀 */
    public static final String CACHE_KEY_PREFIX = "rag:cache:";
    /** redis热点问题缓存时间（秒） */
    public static final int CACHE_EXPIRE_SECONDS = 60 * 60;
    /** redis空问题缓存时间（秒） */
    public static final int CACHE_EMPTY_EXPIRE_SECONDS = 60 * 10;
}
