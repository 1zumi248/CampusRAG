package com.hznu.campusragbackend.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hznu.campusragbackend.model.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationRepository extends BaseMapper<Conversation> {
}
