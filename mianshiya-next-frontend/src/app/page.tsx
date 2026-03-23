"use client";

import { useEffect, useState } from "react";
import Title from "antd/es/typography/Title";
import Paragraph from "antd/es/typography/Paragraph";
import { Button, Card, Divider, Flex, Spin } from "antd";
import Link from "next/link";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { listQuestionVoByPageUsingPost } from "@/api/questionController";
import QuestionBankList from "@/components/QuestionBankList";
import QuestionList from "@/components/QuestionList";
import "./index.css";

/**
 * 主页
 * @constructor
 */
export default function HomePage() {
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>([]);
  const [questionList, setQuestionList] = useState<API.QuestionVO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchHomeData = async () => {
      setLoading(true);
      try {
        const [bankRes, questionRes] = await Promise.all([
          listQuestionBankVoByPageUsingPost({
            pageSize: 12,
            sortField: "createTime",
            sortOrder: "descend",
          }) as Promise<API.BaseResponsePageQuestionBankVO_>,
          listQuestionVoByPageUsingPost({
            pageSize: 12,
            sortField: "createTime",
            sortOrder: "descend",
          }) as Promise<API.BaseResponsePageQuestionVO_>,
        ]);
        setQuestionBankList(bankRes.data?.records ?? []);
        setQuestionList(questionRes.data?.records ?? []);
      } catch (e: any) {
        console.error("获取首页数据失败", e.message);
      } finally {
        setLoading(false);
      }
    };

    fetchHomeData();
  }, []);

  if (loading) {
    return (
      <div id="homePage" className="max-width-content">
        <Card className="home-loading">
          <Spin />
        </Card>
      </div>
    );
  }

  return (
    <div id="homePage" className="max-width-content">
      <section className="home-hero section-shell">
        <div className="home-hero-copy">
          <div className="home-hero-topline">
            <p className="home-brand">面试通</p>
            <span className="home-hero-note">沉淀式准备</span>
          </div>
          <Title>
            <span>把刷题节奏</span>
            <span>放慢一点，</span>
            <span>把准备质量</span>
            <span>拉高一点。</span>
          </Title>
          <Paragraph>
            用更柔和的阅读界面整理题库、沉淀题目、追踪练习记录，让每一次准备都更稳定。
          </Paragraph>
          <div className="home-hero-actions">
            <Link href="/banks">
              <Button type="primary" size="large">
                进入题库
              </Button>
            </Link>
            <Link href="/questions">
              <Button size="large">浏览题目</Button>
            </Link>
          </div>
        </div>
        <div className="home-hero-rail">
          <div className="home-hero-metrics">
            <div className="home-metric">
              <span>题库</span>
              <strong>{questionBankList.length}</strong>
              <small>按方向归档整理</small>
            </div>
            <div className="home-metric">
              <span>最新题目</span>
              <strong>{questionList.length}</strong>
              <small>持续补充新题</small>
            </div>
          </div>
          <div className="home-hero-story">
            <p className="home-story-label">学习路径</p>
            <p className="home-story-copy">
              从题库归档到题目练习，再到模拟面试，让准备过程更连贯，也更容易复盘。
            </p>
            <ul className="home-story-list">
              <li>先按方向整理题库，再集中刷题</li>
              <li>保留练习记录，减少重复走弯路</li>
              <li>最后用模拟面试检查准备质量</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="home-section">
        <Flex justify="space-between" align="center" className="home-section-head">
          <div>
            <p className="home-section-label">题库</p>
            <Title level={3}>最新题库</Title>
          </div>
          <Link href={"/banks"}>查看更多</Link>
        </Flex>
        <QuestionBankList questionBankList={questionBankList} />
      </section>

      <Divider />

      <section className="home-section">
        <Flex justify="space-between" align="center" className="home-section-head">
          <div>
            <p className="home-section-label">题目</p>
            <Title level={3}>最新题目</Title>
          </div>
          <Link href={"/questions"}>查看更多</Link>
        </Flex>
        <QuestionList questionList={questionList} />
      </section>
    </div>
  );
}
