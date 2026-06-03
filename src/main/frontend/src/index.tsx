import {legacyLogicalPropertiesTransformer, StyleProvider} from "@ant-design/cssinjs";
import {App, ConfigProvider, Layout, theme} from "antd";
import zhCN from "antd/es/locale/zh_CN";
import axios from "axios";
import {useEffect, useState} from "react";
import {createRoot} from "react-dom/client";
import AppBase from "./AppBase";

const {darkAlgorithm, defaultAlgorithm} = theme;
const {Content} = Layout;

export interface Plugin {
    id: string;
    version: string;
    name: string;
    paths: string[];
    actions: string[];
    desc: string;
    author: string;
    shortName: string;
    indexPage: string;
    previewImageBase64: string;
    services: string[];
    dependentService: string[];
}

export interface StatisticsConfig {
    host: string;
    retentionDays: number;
}

export interface StatisticsNotificationChannels {
    dailyChannels: string[];
    failedChannels: string[];
}

export interface NotificationProviderRow {
    channel: string;
    providerPluginId: string;
    providerPluginName?: string;
    providerPluginPreviewImageBase64?: string;
    capabilityKey: string;
    capabilityLabel?: string;
    providerStatus: string;
    selected: boolean;
    confirmed: boolean;
    reviewRequired: boolean;
}

export interface StatisticsNotificationChannelInfo {
    settings: StatisticsNotificationChannels;
    providers: NotificationProviderRow[];
}

export interface StatisticsMetric {
    label: string;
    value: number;
    status: "normal" | "processing" | "warning";
}

export interface StatisticsChart {
    type: "line" | "bar" | "donut";
    title: string;
    xField?: string;
    yField?: string;
    nameField?: string;
    valueField?: string;
    unit: string;
    height: number;
    data: Record<string, string | number>[];
}

export interface StatisticsLogRow {
    id: string;
    timestamp: number;
    time: string;
    alias: string;
    path: string;
    url: string;
    referer: string;
    source: string;
    userAgent: string;
    ip: string;
    method: string;
    language: string;
    visitorId: string;
    sessionId: string;
    screenWidth: number;
    screenHeight: number;
    windowWidth: number;
    devicePixelRatio: number;
    deviceType: string;
    viewportRange: string;
    browser: string;
    os: string;
}

export interface StatisticsDailySiteData {
    date: string;
    pv: number;
    uv: number;
    sessions: number;
    uniqueIp: number;
    articleCount: number;
    topArticle: string;
    topArticleViews: number;
    topSource: string;
    topSourceViews: number;
    mobile: number;
    tablet: number;
    desktop: number;
    unknownDevice: number;
}

export interface PageData<T> {
    rows: T[];
    total: number;
    page: number;
    pageSize: number;
}

export interface StatisticsInfoResponse {
    dark: boolean;
    colorPrimary: string;
    plugin: Plugin;
    config: StatisticsConfig;
    notificationChannels: StatisticsNotificationChannels;
    summary: StatisticsMetric[];
    charts: StatisticsChart[];
    dailySiteData: StatisticsDailySiteData[];
    logs: PageData<StatisticsLogRow>;
}

export interface StandardResponse<T> {
    success: boolean;
    message?: string;
    data: T;
}

const loadFromDocument = () => {
    try {
        const node = document.getElementById("pluginInfo");
        if (node === null || node.innerText.length === 0) {
            return null;
        }
        return JSON.parse(node.innerText) as StandardResponse<StatisticsInfoResponse>;
    } catch (e) {
        return null;
    }
};

const Index = () => {
    const [response, setResponse] = useState<StandardResponse<StatisticsInfoResponse> | null>(loadFromDocument);

    useEffect(() => {
        if (response === null) {
            axios.get<StandardResponse<StatisticsInfoResponse>>("json").then(({data}) => {
                setResponse(data);
            });
        }
    }, [response]);

    if (response === null || !response.success) {
        return <></>;
    }

    return (
        <ConfigProvider
            locale={zhCN}
            theme={{
                algorithm: response.data.dark ? darkAlgorithm : defaultAlgorithm,
                token: {
                    colorPrimary: response.data.colorPrimary || "#1677ff",
                },
            }}
        >
            <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                <Content style={{minHeight: "100vh", backgroundColor: response.data.dark ? "#141414" : undefined, color: response.data.dark ? "#dfdfdf" : undefined}}>
                    <App>
                        <AppBase pluginInfo={response.data}/>
                    </App>
                </Content>
            </StyleProvider>
        </ConfigProvider>
    );
};

const container = document.getElementById("app");
const root = createRoot(container!);
root.render(<Index/>);
