"use client";

import {
  Button,
  Card,
  Empty,
  List,
  Pagination,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  deleteMockInterviewUsingPost,
  listMockInterviewVoByPageUsingPost,
} from "@/api/mockInterviewController";
import "./index.css";

interface InterviewReport {
  overallScore?: number;
  summary?: string;
  strengths?: string[];
  weaknesses?: string[];
  suggestions?: string[];
  questionIds?: number[];
  finishedAt?: string;
}

const PAGE_SIZE = 10;

const statusConfig: Record<number, { label: string; color: string; actionText: string }> = {
  0: { label: "待开始", color: "orange", actionText: "进入面试" },
  1: { label: "进行中", color: "green", actionText: "继续面试" },
  2: { label: "已结束", color: "red", actionText: "查看记录" },
};

export default function MockInterviewHistoryPage() {
  const [loading, setLoading] = useState(true);
  const [pageLoading, setPageLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [total, setTotal] = useState(0);
  const [records, setRecords] = useState<API.MockInterview[]>([]);

  const loadData = async (page = current) => {
    if (page === current) {
      setLoading(true);
    } else {
      setPageLoading(true);
    }
    try {
      const res = await listMockInterviewVoByPageUsingPost({
        current: page,
        pageSize: PAGE_SIZE,
        sortField: "createTime",
        sortOrder: "descend",
      });
      setRecords(res.data?.records ?? []);
      setTotal(Number(res.data?.total ?? 0));
      setCurrent(page);
    } catch (error: any) {
      message.error("获取历史面试记录失败，" + error.message);
    } finally {
      setLoading(false);
      setPageLoading(false);
    }
  };

  useEffect(() => {
    loadData(1);
  }, []);

  const deleteInterview = async (id?: number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在删除记录...");
    try {
      await deleteMockInterviewUsingPost({ id });
      hide();
      message.success("删除成功");
      const nextPage = records.length === 1 && current > 1 ? current - 1 : current;
      await loadData(nextPage);
    } catch (error: any) {
      hide();
      message.error("删除失败，" + error.message);
    }
  };

  const renderSummary = (record: API.MockInterview) => {
    if (!record.report) {
      return (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {record.status === 0
            ? "面试尚未开始，进入后会从所选题库中按难度抽取题目。"
            : "面试进行中，继续作答后将自动沉淀对话与报告。"}
        </Typography.Paragraph>
      );
    }
    try {
      const report = JSON.parse(record.report) as InterviewReport;
      return (
        <div className="record-summary">
          <Space wrap size={[8, 8]}>
            <Tag color="purple">得分：{report.overallScore ?? "-"}</Tag>
            {report.finishedAt ? <Tag>完成时间：{report.finishedAt}</Tag> : null}
          </Space>
          {report.summary ? (
            <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
              {report.summary}
            </Typography.Paragraph>
          ) : null}
        </div>
      );
    } catch (error) {
      return (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          面试报告已生成，进入详情页可查看完整内容。
        </Typography.Paragraph>
      );
    }
  };

  return (
    <div id="mockInterviewHistoryPage" className="max-width-content">
      <div className="history-header">
        <div>
          <Typography.Title level={2}>历史面试记录</Typography.Title>
          <Typography.Paragraph>
            这里会保留你创建过的模拟面试、对话过程以及结束后的结构化报告。
          </Typography.Paragraph>
        </div>
        <Link href="/mockInterview/add">
          <Button type="primary" size="large">
            新建模拟面试
          </Button>
        </Link>
      </div>

      {loading ? (
        <Card className="history-shell">
          <Spin />
        </Card>
      ) : records.length === 0 ? (
        <Card className="history-shell">
          <Empty
            description="还没有历史面试记录"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Link href="/mockInterview/add">
              <Button type="primary">去创建第一场面试</Button>
            </Link>
          </Empty>
        </Card>
      ) : (
        <Card className="history-shell">
          <List
            loading={pageLoading}
            dataSource={records}
            itemLayout="vertical"
            renderItem={(record) => {
              const status = statusConfig[record.status ?? 0] ?? statusConfig[0];
              return (
                <List.Item
                  key={record.id}
                  actions={[
                    <Link key="enter" href={`/mockInterview/chat/${record.id}`}>
                      {status.actionText}
                    </Link>,
                    <Popconfirm
                      key="delete"
                      title="确认删除这条面试记录吗？"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => deleteInterview(record.id)}
                    >
                      <a>删除</a>
                    </Popconfirm>,
                  ]}
                >
                  <Space direction="vertical" size={12} style={{ width: "100%" }}>
                    <div className="record-top">
                      <Space wrap size={[8, 8]}>
                        <Typography.Title level={4} style={{ margin: 0 }}>
                          模拟面试 #{record.id}
                        </Typography.Title>
                        <Tag color={status.color}>{status.label}</Tag>
                        {record.topic ? <Tag color="blue">题库：{record.topic}</Tag> : null}
                        {record.difficulty ? <Tag color="gold">难度：{record.difficulty}</Tag> : null}
                      </Space>
                      {record.createTime ? (
                        <Typography.Text type="secondary">
                          创建时间：{new Date(record.createTime).toLocaleString()}
                        </Typography.Text>
                      ) : null}
                    </div>
                    <Typography.Paragraph className="record-meta">
                      岗位：{record.jobPosition || "-"} · 年限：{record.workExperience || "-"}
                    </Typography.Paragraph>
                    {renderSummary(record)}
                  </Space>
                </List.Item>
              );
            }}
          />

          <div className="history-pagination">
            <Pagination
              current={current}
              pageSize={PAGE_SIZE}
              total={total}
              onChange={(page) => loadData(page)}
              showSizeChanger={false}
            />
          </div>
        </Card>
      )}
    </div>
  );
}
