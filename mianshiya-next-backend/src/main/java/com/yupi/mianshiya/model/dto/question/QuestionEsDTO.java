package com.yupi.mianshiya.model.dto.question;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.mianshiya.model.entity.Question;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

// ES 文档模型：专门用于“题目检索”的索引结构，不直接等价于数据库实体。
// 设计目标：
// 1) 只放检索相关字段，降低索引更新成本；
// 2) 文本字段绑定 ik 分词，提升中文召回能力；
// 3) 通过 id 与数据库实体一一对应，便于回库补全最新数据。
@Document(indexName = "question")
@Data
public class QuestionEsDTO implements Serializable {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * ES 文档主键，直接复用数据库 question.id。
     * 这样 saveAll 时天然具备幂等覆盖能力（同 id 会更新而不是新增）。
     */
    @Id
    private Long id;

    /**
     * 标题：使用 ik_max_word 建索引，ik_smart 做查询分词。
     * - 索引时尽可能细粒度切词，提高召回
     * - 查询时较粗粒度切词，减少噪声
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /**
     * 题目内容，全文检索字段。
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    /**
     * 推荐答案，全文检索字段。
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String answer;

    /**
     * 标签列表：按 keyword 存储，用于精确过滤（termQuery）。
     */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /**
     * 创建用户 id
     */
    @Field(type = FieldType.Long)
    private Long userId;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    /**
     * 是否删除
     */
    @Field(type = FieldType.Integer)
    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    /**
     * DB 实体 -> ES 文档
     * - 复制基础字段
     * - 将 DB 中 JSON 字符串 tags 反序列化为 List，便于 ES keyword 过滤
     *
     * @param question
     * @return
     */
    public static QuestionEsDTO objToDto(Question question) {
        if (question == null) {
            return null;
        }
        QuestionEsDTO questionEsDTO = new QuestionEsDTO();
        BeanUtils.copyProperties(question, questionEsDTO);
        String tagsStr = question.getTags();
        if (StrUtil.isNotBlank(tagsStr)) {
            questionEsDTO.setTags(JSONUtil.toList(JSONUtil.parseArray(tagsStr), String.class));
        }
        return questionEsDTO;
    }

    /**
     * ES 文档 -> DB 实体对象（仅用于中间转换）
     * - 将 List<String> tags 重新序列化为 DB 中的 JSON 字符串格式
     *
     * @param questionEsDTO
     * @return
     */
    public static Question dtoToObj(QuestionEsDTO questionEsDTO) {
        if (questionEsDTO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEsDTO, question);
        List<String> tagList = questionEsDTO.getTags();
        if (CollUtil.isNotEmpty(tagList)) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        return question;
    }
}
