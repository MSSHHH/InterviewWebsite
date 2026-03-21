package com.yupi.mianshiya.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.yupi.mianshiya.esdao.QuestionEsDao;
import com.yupi.mianshiya.mapper.QuestionMapper;
import com.yupi.mianshiya.model.dto.question.QuestionEsDTO;
import com.yupi.mianshiya.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量同步题目到 es
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次增量同步。
     *
     * 同步策略说明：
     * 1) 以 updateTime 窗口拉取最近变更（包含新增、编辑、逻辑删除）；
     * 2) 转为 ES 文档后批量写入；
     * 3) 依赖 ES 文档 id = 数据库 id，实现幂等覆盖，避免重复数据。
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        // 这里用“5 分钟回看窗口”而不是“1 分钟”，是为了给任务抖动 / 短暂故障留冗余，
        // 即使某次调度延迟，也能在后续轮次补上遗漏数据。
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        // 查询包含逻辑删除的数据，确保 ES 中 isDelete 等状态能被及时覆盖
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        if (CollUtil.isEmpty(questionList)) {
            log.info("no inc question");
            return;
        }
        // DB 实体 -> ES 文档
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        // 分批写入，避免单批过大导致内存或网络压力
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("IncSyncQuestionToEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);
            log.info("sync from {} to {}", i, end);
            // saveAll 会按文档 id 覆盖同名记录，保证同步幂等
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("IncSyncQuestionToEs end, total {}", total);
    }
}
