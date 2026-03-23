import Title from "antd/es/typography/Title";
import QuestionTable from "@/components/QuestionTable";
import "./index.css";

/**
 * 题目列表页面
 * @constructor
 */
export default function QuestionsPage({
  searchParams,
}: {
  searchParams?: { q?: string };
}) {
  return (
    <div id="questionsPage" className="max-width-content">
      <Title level={3}>题目大全</Title>
      <QuestionTable
        defaultSearchParams={{
          searchText: searchParams?.q,
        }}
      />
    </div>
  );
}
