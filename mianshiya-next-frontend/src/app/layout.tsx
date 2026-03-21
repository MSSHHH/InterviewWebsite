"use client";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import BasicLayout from "@/layouts/BasicLayout";
import React, { useCallback, useEffect } from "react";
import { Provider, useDispatch } from "react-redux";
import store, { AppDispatch } from "@/stores";
import { getLoginUserUsingGet } from "@/api/userController";
import AccessLayout from "@/access/AccessLayout";
import { setLoginUser } from "@/stores/loginUser";
import { DEFAULT_USER } from "@/constants/user";
import "./globals.css";

/**
 * 全局初始化逻辑
 * @param children
 * @constructor
 */
const InitLayout: React.FC<
  Readonly<{
    children: React.ReactNode;
  }>
> = ({ children }) => {
  const dispatch = useDispatch<AppDispatch>();
  // 初始化全局用户状态
  const doInitLoginUser = useCallback(async () => {
    try {
      const res = await getLoginUserUsingGet();
      if (res.data) {
        // 更新全局用户状态
        dispatch(setLoginUser(res.data));
      }
    } catch (error) {
      // 后端暂时不可用时保持未登录态，避免未捕获异常打断页面渲染
      dispatch(setLoginUser(DEFAULT_USER));
      console.error("初始化登录用户失败", error);
    }
  }, [dispatch]);

  // 只执行一次
  useEffect(() => {
    doInitLoginUser();
  }, [doInitLoginUser]);
  return children;
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh">
      <body>
        <AntdRegistry>
          <Provider store={store}>
            <InitLayout>
              <BasicLayout>
                <AccessLayout>{children}</AccessLayout>
              </BasicLayout>
            </InitLayout>
          </Provider>
        </AntdRegistry>
      </body>
    </html>
  );
}
