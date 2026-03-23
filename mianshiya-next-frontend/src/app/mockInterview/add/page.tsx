"use client";
import { Button, Form, Input, Select, message } from "antd";
import React, { useEffect, useMemo, useState } from "react";
import { addMockInterviewUsingPost } from "@/api/mockInterviewController";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { useRouter } from "next/navigation";
import Link from "next/link";
import "./index.css";

interface Props {}

/**
 * 创建 AI 模拟面试页面
 * @param props
 * @constructor
 */
const CreateMockInterviewPage: React.FC<Props> = (props) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>([]);
  const router = useRouter();

  const questionBankOptions = useMemo(
    () =>
      questionBankList.map((questionBank) => ({
        label: questionBank.title ?? "",
        value: questionBank.id ?? 0,
      })),
    [questionBankList],
  );

  const loadQuestionBanks = async () => {
    try {
      const res = await listQuestionBankVoByPageUsingPost({
        pageSize: 200,
        sortField: "createTime",
        sortOrder: "descend",
      });
      setQuestionBankList(res.data?.records ?? []);
    } catch (error: any) {
      message.error("获取题库列表失败，" + error.message);
    }
  };

  useEffect(() => {
    loadQuestionBanks();
  }, []);

  /**
   * 提交表单
   *
   * @param values
   */
  const doSubmit = async (values: API.MockInterviewAddRequest) => {
    const hide = message.loading("正在创建模拟面试...");
    setLoading(true);
    try {
      const selectedQuestionBank = questionBankList.find((item) => item.id === values.questionBankId);
      const res = await addMockInterviewUsingPost({
        ...values,
        topic: selectedQuestionBank?.title,
      });
      hide();
      message.success("模拟面试创建成功");
      form.resetFields(); // 重置表单
      // 跳转到模拟面试列表页面
      router.push("/mockInterview/chat/" + res.data);
    } catch (error: any) {
      hide();
      message.error("创建失败，" + error.message);
    }
    setLoading(false);
  };

  return (
    <div id="createMockInterviewPage">
      <div className="page-header">
        <h2>创建 AI 模拟面试</h2>
        <Link href="/mockInterview" className="history-link">
          查看历史记录
        </Link>
      </div>
      <Form form={form} style={{ marginTop: 24 }} onFinish={doSubmit}>
        {/* 工作岗位 */}
        <Form.Item label="工作岗位" name="jobPosition">
          <Input placeholder="请输入工作岗位，例如：Java 开发工程师" />
        </Form.Item>

        {/* 工作年限 */}
        <Form.Item label="工作年限" name="workExperience">
          <Input placeholder="请输入工作年限，例如：3 年" />
        </Form.Item>

        {/* 面试难度 */}
        <Form.Item label="面试难度" name="difficulty">
          <Select
            placeholder="请选择面试难度"
            options={[
              { label: "简单", value: "easy" },
              { label: "中等", value: "medium" },
              { label: "困难", value: "hard" },
            ]}
          />
        </Form.Item>

        {/* 面试方向 */}
        <Form.Item
          label="面试方向"
          name="questionBankId"
          rules={[{ required: true, message: "请选择题库方向" }]}
        >
          <Select
            showSearch
            placeholder="请选择题库方向，可输入关键词搜索"
            optionFilterProp="label"
            options={questionBankOptions}
          />
        </Form.Item>

        {/* 提交按钮 */}
        <Form.Item>
          <Button
            loading={loading}
            style={{ width: 180 }}
            type="primary"
            htmlType="submit"
          >
            创建模拟面试
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default CreateMockInterviewPage;
