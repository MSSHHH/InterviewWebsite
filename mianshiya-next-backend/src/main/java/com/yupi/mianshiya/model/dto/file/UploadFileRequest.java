package com.yupi.mianshiya.model.dto.file;

import java.io.Serializable;
import lombok.Data;

/**
 * 文件上传请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Data
public class UploadFileRequest implements Serializable {

    /**
     * 上传业务类型。
     * 典型值见 FileUploadBizEnum，例如：
     * - user_avatar：用户头像
     * - question_bank_picture：题库封面
     * 不同业务会走不同的目录和校验策略。
     */
    private String biz;

    private static final long serialVersionUID = 1L;
}
