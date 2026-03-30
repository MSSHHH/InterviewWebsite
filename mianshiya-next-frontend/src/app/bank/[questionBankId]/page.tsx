"use client";

import { useEffect, useState } from "react";
import { Avatar, Button, Card, Spin } from "antd";
import { getQuestionBankVoByIdUsingGet } from "@/api/questionBankController";
import { listQuestionVoByPageUsingPost } from "@/api/questionController";
import Meta from "antd/es/card/Meta";
import Paragraph from "antd/es/typography/Paragraph";
import Title from "antd/es/typography/Title";
import QuestionList from "@/components/QuestionList";
import "./index.css";

/**
 * 题库详情页
 * @constructor
 */
export default function BankPage({
  params,
}: {
  params: { questionBankId: string };
}) {
  const FIRST_PAGE_SIZE = 20;
  const { questionBankId } = params;
  const [bank, setBank] = useState<API.QuestionBankVO>();
  const [questionList, setQuestionList] = useState<API.QuestionVO[]>([]);
  const [questionTotal, setQuestionTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    const fetchBankDetail = async () => {
      setLoading(true);
      setLoadError("");
      try {
        const [bankRes, questionRes] = await Promise.all([
          getQuestionBankVoByIdUsingGet({
            id: Number(questionBankId),
          }) as Promise<API.BaseResponseQuestionBankVO_>,
          listQuestionVoByPageUsingPost({
            questionBankId: Number(questionBankId),
            current: 1,
            pageSize: FIRST_PAGE_SIZE,
          }) as Promise<API.BaseResponsePageQuestionVO_>,
        ]);
        setBank(bankRes.data);
        setQuestionList(questionRes.data?.records ?? []);
        setQuestionTotal(questionRes.data?.total ?? 0);
      } catch (e: any) {
        const errorMessage = "获取题库详情失败，请刷新重试";
        console.error("获取题库详情失败，" + e.message);
        setLoadError(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    fetchBankDetail();
  }, [questionBankId]);

  if (loading) {
    return (
      <div id="bankPage" className="max-width-content">
        <Card>
          <Spin />
        </Card>
      </div>
    );
  }

  // 错误处理
  if (!bank) {
    return <div>{loadError || "获取题库详情失败，请刷新重试"}</div>;
  }

  // 获取第一道题目，用于 “开始刷题” 按钮跳转
  let firstQuestionId;
  if (questionList.length > 0) {
    firstQuestionId = questionList[0].id;
  }

  return (
    <div id="bankPage" className="max-width-content">
      <Card>
        <Meta
          avatar={<Avatar src={bank.picture} size={72} />}
          title={
            <Title level={3} style={{ marginBottom: 0 }}>
              {bank.title}
            </Title>
          }
          description={
            <>
              <Paragraph type="secondary">{bank.description}</Paragraph>
              <Button
                type="primary"
                shape="round"
                href={`/bank/${questionBankId}/question/${firstQuestionId}`}
                target="_blank"
                disabled={!firstQuestionId}
              >
                开始刷题
              </Button>
            </>
          }
        />
      </Card>
      <div style={{ marginBottom: 16 }} />
      <QuestionList
        questionBankId={questionBankId}
        questionList={questionList}
        cardTitle={`题目列表（${questionTotal}）`}
      />
    </div>
  );
}
