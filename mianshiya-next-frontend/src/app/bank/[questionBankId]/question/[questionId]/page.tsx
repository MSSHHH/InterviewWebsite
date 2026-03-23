"use client";

import { useEffect, useState } from "react";
import { Card, Flex, Menu, Spin } from "antd";
import { getQuestionBankVoByIdUsingGet } from "@/api/questionBankController";
import Title from "antd/es/typography/Title";
import { getQuestionVoByIdUsingGet } from "@/api/questionController";
import Sider from "antd/es/layout/Sider";
import { Content } from "antd/es/layout/layout";
import QuestionCard from "@/components/QuestionCard";
import Link from "next/link";
import "./index.css";

/**
 * 题库题目详情页
 * @constructor
 */
export default function BankQuestionPage({
  params,
}: {
  params: { questionBankId: string; questionId: string };
}) {
  const { questionBankId, questionId } = params;
  const [bank, setBank] = useState<API.QuestionBankVO>();
  const [question, setQuestion] = useState<API.QuestionVO>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    const fetchPageData = async () => {
      setLoading(true);
      setLoadError("");
      try {
        const [bankRes, questionRes] = await Promise.all([
          getQuestionBankVoByIdUsingGet({
            id: questionBankId,
            needQueryQuestionList: true,
            pageSize: 200,
          }) as Promise<API.BaseResponseQuestionBankVO_>,
          getQuestionVoByIdUsingGet({
            id: questionId,
          }) as Promise<API.BaseResponseQuestionVO_>,
        ]);
        setBank(bankRes.data);
        setQuestion(questionRes.data);
      } catch (e: any) {
        const errorMessage = "获取题目详情失败，请刷新重试";
        console.error("获取题库题目详情失败", e.message);
        setLoadError(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    fetchPageData();
  }, [questionBankId, questionId]);

  if (loading) {
    return (
      <div id="bankQuestionPage">
        <Card>
          <Spin />
        </Card>
      </div>
    );
  }

  if (!bank) {
    return <div>{loadError || "获取题库详情失败，请刷新重试"}</div>;
  }

  if (!question) {
    return <div>{loadError || "获取题目详情失败，请刷新重试"}</div>;
  }

  const questionMenuItemList = (bank.questionPage?.records || []).map((q) => {
    return {
      label: (
        <Link href={`/bank/${questionBankId}/question/${q.id}`}>{q.title}</Link>
      ),
      key: String(q.id),
    };
  });

  return (
    <div id="bankQuestionPage">
      <Flex gap={24}>
        <Sider width={240} theme="light" style={{ padding: "24px 0" }}>
          <Title level={4} style={{ padding: "0 20px" }}>
            {bank.title}
          </Title>
          <Menu items={questionMenuItemList} selectedKeys={[String(question.id)]} />
        </Sider>
        <Content>
          <QuestionCard question={question} />
        </Content>
      </Flex>
    </div>
  );
}
