package com.hznu.campusragbackend.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hznu.campusragbackend.model.Document;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentRepository extends BaseMapper<Document> {
}
