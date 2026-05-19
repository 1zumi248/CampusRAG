package com.hznu.campusragbackend.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hznu.campusragbackend.model.MessageRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageRecordRepository extends BaseMapper<MessageRecord> {
}
