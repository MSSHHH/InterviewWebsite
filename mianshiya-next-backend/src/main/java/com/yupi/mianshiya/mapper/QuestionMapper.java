package com.yupi.mianshiya.mapper;

import com.yupi.mianshiya.model.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
* @author 李鱼皮
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2024-08-24 21:46:47
* @Entity com.yupi.mianshiya.model.entity.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 查询指定时间点之后发生变更的题目（包含逻辑删除）。
     *
     * 说明：
     * - 这里故意不加 isDelete = 0 过滤，目的是让“删除状态”也能同步到 ES；
     * - 供定时增量同步任务调用，按 updateTime 做窗口拉取。
     */
    @Select("select * from question where updateTime >= #{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date minUpdateTime);

}
