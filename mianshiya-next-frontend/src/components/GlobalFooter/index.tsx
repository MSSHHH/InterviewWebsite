import React from "react";
import "./index.css";

/**
 * 全局底部栏组件
 * @constructor
 */
export default function GlobalFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <div className="global-footer">
      <div>© {currentYear} 面试通</div>
      <div>
        <a href="https://www.code-nav.cn" target="_blank">
          开源学习支持：编程导航
        </a>
      </div>
    </div>
  );
}
