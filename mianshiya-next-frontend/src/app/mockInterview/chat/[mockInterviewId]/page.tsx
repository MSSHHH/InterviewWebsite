"use client";
import { Button, Card, Divider, Input, List, Space, Tag, Typography, message } from "antd";
import React, { useEffect, useRef, useState } from "react";
import {
  getMockInterviewByIdUsingGet,
  handleMockInterviewEventUsingPost,
} from "@/api/mockInterviewController";
import MdViewer from "@/components/MdViewer";
import "./index.css";

interface InterviewMessage {
  role?: string;
  message?: string;
  questionId?: number;
  questionTitle?: string;
  toolName?: string;
  pending?: boolean;
}

interface MockInterviewDetail extends API.MockInterview {
  parsedMessages?: InterviewMessage[];
}

interface InterviewReport {
  overallScore?: number;
  summary?: string;
  strengths?: string[];
  weaknesses?: string[];
  suggestions?: string[];
  questionIds?: number[];
  finishedAt?: string;
}

export default function InterviewRoomPage({ params }) {
  const { mockInterviewId } = params;
  const [loadingInterview, setLoadingInterview] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [inputMessage, setInputMessage] = useState("");
  const [interview, setInterview] = useState<MockInterviewDetail>();
  const [messages, setMessages] = useState<InterviewMessage[]>([]);
  const [report, setReport] = useState<InterviewReport>();
  const [isStarted, setIsStarted] = useState(false);
  const [isEnded, setIsEnded] = useState(false);
  const messageAreaRef = useRef<HTMLDivElement>(null);

  const syncInterviewState = (data?: MockInterviewDetail) => {
    if (!data) {
      return;
    }
    if (data.messages) {
      try {
        data.parsedMessages = JSON.parse(data.messages);
        setMessages(data.parsedMessages || []);
      } catch (error) {
        setMessages([]);
      }
    } else {
      setMessages([]);
    }

    if (data.report) {
      try {
        setReport(JSON.parse(data.report));
      } catch (error) {
        setReport(undefined);
      }
    } else {
      setReport(undefined);
    }

    setInterview(data);
    setIsStarted(data.status === 1);
    setIsEnded(data.status === 2);
  };

  // 加载面试数据
  const loadInterview = async () => {
    try {
      setLoadingInterview(true);
      const res = await getMockInterviewByIdUsingGet({ id: mockInterviewId });
      syncInterviewState(res.data as MockInterviewDetail);
    } catch (error) {
      message.error("加载面试数据失败");
    } finally {
      setLoadingInterview(false);
    }
  };

  useEffect(() => {
    loadInterview();
  }, []);

  useEffect(() => {
    if (!messageAreaRef.current) {
      return;
    }
    messageAreaRef.current.scrollTop = messageAreaRef.current.scrollHeight;
  }, [messages, report]);

  // 处理事件
  const handleEvent = async (eventType: string, msg?: string) => {
    const isChat = eventType === "chat";
    const trimmedMsg = msg?.trim();
    if (isChat && !trimmedMsg) {
      return;
    }

    const optimisticUserMessage: InterviewMessage | null =
      isChat && trimmedMsg
        ? {
            role: "user",
            message: trimmedMsg,
          }
        : null;

    const pendingAssistantMessage: InterviewMessage = {
      role: "assistant",
      message: "AI 正在思考，请稍候...",
      pending: true,
    };

    if (optimisticUserMessage) {
      setMessages((prev) => [...prev, optimisticUserMessage, pendingAssistantMessage]);
    } else {
      setMessages((prev) => [...prev, pendingAssistantMessage]);
    }

    if (isChat) {
      setSending(true);
    } else {
      setActionLoading(true);
    }

    try {
      const res = await handleMockInterviewEventUsingPost({
        event: eventType,
        id: interview?.id,
        message: trimmedMsg,
      });
      const latestInterview = res.data?.mockInterview as MockInterviewDetail | undefined;
      syncInterviewState(latestInterview);
    } catch (error: any) {
      setMessages((prev) => prev.filter((item) => !item.pending));
      message.error("操作失败，" + (error?.message || ""));
    } finally {
      setActionLoading(false);
      setSending(false);
    }
  };

  // 发送消息
  const sendMessage = async () => {
    if (!inputMessage.trim()) return;
    const currentMessage = inputMessage;
    setInputMessage("");
    await handleEvent("chat", currentMessage);
  };

  return (
    <div id="interviewRoomPage" className="max-width-content">
      {/* 标题 */}
      <div className="header">
        <Space direction="vertical" size={6}>
          <h1>模拟面试 #{interview?.id}</h1>
          <Space wrap>
            <Tag color={isEnded ? "red" : isStarted ? "green" : "orange"}>
              {isEnded ? "已结束" : isStarted ? "进行中" : "待开始"}
            </Tag>
            {interview?.topic ? <Tag color="blue">方向：{interview.topic}</Tag> : null}
            {interview?.difficulty ? (
              <Tag color="gold">难度：{interview.difficulty}</Tag>
            ) : null}
          </Space>
        </Space>
      </div>

      {/* 操作按钮 */}
      <div className="action-buttons">
        <Button
          type="primary"
          onClick={() => handleEvent("start")}
          disabled={isStarted || isEnded}
          loading={actionLoading}
        >
          开始面试
        </Button>
        <Button
          danger
          onClick={() => handleEvent("end")}
          disabled={!isStarted || isEnded}
          loading={actionLoading}
        >
          结束面试
        </Button>
      </div>

      {/* 消息列表 */}
      <Card className="message-area" ref={messageAreaRef} loading={loadingInterview}>
        <List
          dataSource={messages}
          split={false}
          renderItem={(item) => (
            <List.Item
              style={{
                justifyContent: item.role === "assistant" ? "flex-start" : "flex-end",
              }}
            >
              <div className={`message-bubble ${item.role === "assistant" ? "ai" : "user"}`}>
                {item.questionTitle ? (
                  <div className="message-title">当前题目：{item.questionTitle}</div>
                ) : null}
                <div className="message-content">
                  {item.pending ? (
                    <Typography.Text type="secondary">AI 正在思考，请稍候...</Typography.Text>
                  ) : item.role === "assistant" ? (
                    <MdViewer value={item.message} />
                  ) : (
                    <div className="plain-message">{item.message}</div>
                  )}
                </div>
              </div>
            </List.Item>
          )}
        />
      </Card>

      {isEnded && report ? (
        <Card className="message-area">
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              面试报告
            </Typography.Title>
            <Space wrap>
              <Tag color="purple">得分：{report.overallScore ?? "-"}</Tag>
              {report.finishedAt ? <Tag>完成时间：{report.finishedAt}</Tag> : null}
            </Space>
            {report.summary ? <MdViewer value={report.summary} /> : null}
            <Divider style={{ margin: "8px 0" }} />
            <div>
              <Typography.Text strong>优点</Typography.Text>
              <List
                size="small"
                dataSource={report.strengths || []}
                renderItem={(item) => <List.Item>{item}</List.Item>}
              />
            </div>
            <div>
              <Typography.Text strong>问题</Typography.Text>
              <List
                size="small"
                dataSource={report.weaknesses || []}
                renderItem={(item) => <List.Item>{item}</List.Item>}
              />
            </div>
            <div>
              <Typography.Text strong>建议</Typography.Text>
              <List
                size="small"
                dataSource={report.suggestions || []}
                renderItem={(item) => <List.Item>{item}</List.Item>}
              />
            </div>
          </Space>
        </Card>
      ) : null}

      {/* 输入区域 */}
      <div className="input-area">
        <Input.TextArea
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          placeholder="输入你的回答..."
          disabled={!isStarted || isEnded}
          rows={3}
        />
        <Button
          type="primary"
          onClick={sendMessage}
          loading={sending}
          disabled={!isStarted || isEnded}
        >
          发送
        </Button>
      </div>
    </div>
  );
}
