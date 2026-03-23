"use client";

import { useEffect, useState } from "react";
import { Card, Spin } from "antd";
import Title from "antd/es/typography/Title";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import QuestionBankList from "@/components/QuestionBankList";
import "./index.css";

/**
 * 题库列表页面
 * @constructor
 */
export default function BanksPage() {
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchBankList = async () => {
      setLoading(true);
      try {
        const res = (await listQuestionBankVoByPageUsingPost({
          pageSize: 200,
          sortField: "createTime",
          sortOrder: "descend",
        })) as API.BaseResponsePageQuestionBankVO_;
        setQuestionBankList(res.data?.records ?? []);
      } catch (e: any) {
        console.error("获取题库列表失败", e.message);
      } finally {
        setLoading(false);
      }
    };

    fetchBankList();
  }, []);

  return (
    <div id="banksPage" className="max-width-content">
      <Title level={3}>题库大全</Title>
      {loading ? (
        <Card>
          <Spin />
        </Card>
      ) : (
        <QuestionBankList questionBankList={questionBankList} />
      )}
    </div>
  );
}
