package com.yupi.mianshiya.job.once;

import com.yupi.mianshiya.esdao.QuestionEsDao;
import com.yupi.mianshiya.model.dto.question.QuestionEsDTO;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.service.QuestionService;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.collection.CollUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 全量同步题目到 es
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */

// 保留历史类名兼容，实际题目全量同步任务使用 FullSyncQuestionToEs
// @Component
@Slf4j
@Deprecated
public class FullSyncPostToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) {
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            return;
        }
        List<QuestionEsDTO> questionEsDTOList = questionList.stream().map(QuestionEsDTO::objToDto).collect(Collectors.toList());
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncPostToEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);
            log.info("sync from {} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncPostToEs end, total {}", total);
    }
}
