"use client";

import { useEffect, useState } from "react";
import { Card, Spin } from "antd";
import { getQuestionVoByIdUsingGet } from "@/api/questionController";
import QuestionCard from "@/components/QuestionCard";
import "./index.css";

/**
 * 题目详情页
 * @constructor
 */
export default function QuestionPage({
  params,
}: {
  params: { questionId: string };
}) {
  const { questionId } = params;
  const [question, setQuestion] = useState<API.QuestionVO>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    const fetchQuestionDetail = async () => {
      setLoading(true);
      setLoadError("");
      try {
        const res = (await getQuestionVoByIdUsingGet({
          id: questionId,
        })) as API.BaseResponseQuestionVO_;
        setQuestion(res.data);
      } catch (e: any) {
        const errorMessage = "获取题目详情失败，请刷新重试";
        console.error("获取题目详情失败", e.message);
        setLoadError(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    fetchQuestionDetail();
  }, [questionId]);

  if (loading) {
    return (
      <div id="questionPage">
        <Card>
          <Spin />
        </Card>
      </div>
    );
  }

  if (!question) {
    return <div>{loadError || "获取题目详情失败，请刷新重试"}</div>;
  }

  return (
    <div id="questionPage">
      <QuestionCard question={question} />
    </div>
  );
}
