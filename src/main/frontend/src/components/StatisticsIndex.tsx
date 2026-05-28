import {CopyOutlined, ReloadOutlined, SettingOutlined} from "@ant-design/icons";
import {Bar, Line, Pie} from "@ant-design/plots";
import type {ColumnsType} from "antd/es/table";
import {
    Alert,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    Flex,
    Form,
    Grid,
    Input,
    Row,
    Select,
    Space,
    Statistic,
    Table,
    Tag,
    Tooltip,
    Typography,
    message,
    theme,
} from "antd";
import axios from "axios";
import {FunctionComponent, useMemo, useState} from "react";
import {
    PageData,
    StandardResponse,
    StatisticsChart,
    StatisticsConfig,
    StatisticsInfoResponse,
    StatisticsLogRow,
    StatisticsMetric,
} from "../index";

type StatisticsIndexProps = {
    data: StatisticsInfoResponse;
}

type FilterValues = {
    keyword?: string;
    source?: string;
    alias?: string;
}

const retentionOptions = [
    {label: "30 天", value: 30},
    {label: "90 天", value: 90},
    {label: "180 天", value: 180},
];

const sourceOptions = [
    {label: "全部来源", value: ""},
    {label: "直接访问", value: "直接访问"},
    {label: "搜索引擎", value: "搜索引擎"},
    {label: "外部链接", value: "外部链接"},
];

const request = async <T, >(url: string, params?: Record<string, string>) => {
    const {data} = await axios.post<StandardResponse<T>>(url, new URLSearchParams(params), {
        headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
    });
    if (!data.success) {
        throw new Error(data.message || "操作失败");
    }
    return data.data;
};

const fetchLogs = async (params: Record<string, string>) => {
    const {data} = await axios.get<StandardResponse<PageData<StatisticsLogRow>>>("list", {params});
    if (!data.success) {
        throw new Error(data.message || "加载失败");
    }
    return data.data;
};

const statusColor = (status: StatisticsMetric["status"]) => {
    if (status === "processing") {
        return "processing";
    }
    if (status === "warning") {
        return "warning";
    }
    return "default";
};

const ellipsis = (value: string, max = 42) => {
    if (!value) {
        return "-";
    }
    if (value.length <= max) {
        return value;
    }
    return `${value.slice(0, max)}...`;
};

const displayViewport = (row: StatisticsLogRow) => {
    if (!row.windowWidth && !row.screenWidth) {
        return "未知";
    }
    const screen = row.screenWidth && row.screenHeight ? `${row.screenWidth} x ${row.screenHeight}` : "未知屏幕";
    const dpr = row.devicePixelRatio ? `DPR ${row.devicePixelRatio}` : "DPR -";
    return `${row.windowWidth || "-"}px / ${screen} / ${dpr}`;
};

const shortId = (value: string) => {
    if (!value) {
        return "-";
    }
    if (value.length <= 10) {
        return value;
    }
    return value.slice(0, 8);
};

const ChartBlock: FunctionComponent<{ chart: StatisticsChart; colorPrimary: string }> = ({chart, colorPrimary}) => {
    if (chart.data.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据"/>;
    }
    const common = {
        data: chart.data,
        height: chart.height,
        autoFit: true,
        colorField: chart.nameField,
    } as any;
    if (chart.type === "line") {
        return (
            <Line
                {...common}
                xField={chart.xField}
                yField={chart.yField}
                colorField={chart.nameField}
                color={chart.nameField ? undefined : colorPrimary}
                point={{size: 3}}
            />
        );
    }
    if (chart.type === "bar") {
        return (
            <Bar
                {...common}
                xField={chart.yField}
                yField={chart.xField}
                colorField={undefined}
                color={colorPrimary}
                scrollbar={{y: chart.data.length > 6 ? {} : undefined}}
            />
        );
    }
    return (
        <Pie
            {...common}
            angleField={chart.valueField}
            colorField={chart.nameField}
            innerRadius={0.62}
            label={false}
            legend={{position: "bottom"}}
        />
    );
};

const StatisticsIndex: FunctionComponent<StatisticsIndexProps> = ({data}) => {
    const [config, setConfig] = useState<StatisticsConfig>(data.config);
    const [metrics, setMetrics] = useState<StatisticsMetric[]>(data.summary || []);
    const [charts, setCharts] = useState<StatisticsChart[]>(data.charts || []);
    const [logs, setLogs] = useState<PageData<StatisticsLogRow>>(data.logs);
    const [filters, setFilters] = useState<FilterValues>({});
    const [loading, setLoading] = useState(false);
    const [settingOpen, setSettingOpen] = useState(false);
    const [detail, setDetail] = useState<StatisticsLogRow | null>(null);
    const [form] = Form.useForm<StatisticsConfig>();
    const [messageApi, contextHolder] = message.useMessage();
    const {token} = theme.useToken();
    const screens = Grid.useBreakpoint();

    const embedCode = '<plugin name="statistics" view="widget"/>';

    const loadLogs = async (page = logs.page, pageSize = logs.pageSize, nextFilters = filters) => {
        setLoading(true);
        try {
            setLogs(await fetchLogs({
                page: String(page),
                pageSize: String(pageSize),
                keyword: nextFilters.keyword || "",
                source: nextFilters.source || "",
                alias: nextFilters.alias || "",
            }));
        } catch (e) {
            messageApi.error(e instanceof Error ? e.message : "加载失败");
        } finally {
            setLoading(false);
        }
    };

    const refreshPage = async () => {
        setLoading(true);
        try {
            const {data: response} = await axios.get<StandardResponse<StatisticsInfoResponse>>("json");
            if (!response.success) {
                throw new Error(response.message || "加载失败");
            }
            setConfig(response.data.config);
            setMetrics(response.data.summary || []);
            setCharts(response.data.charts || []);
            setLogs(response.data.logs);
        } catch (e) {
            messageApi.error(e instanceof Error ? e.message : "加载失败");
        } finally {
            setLoading(false);
        }
    };

    const openSetting = () => {
        form.setFieldsValue(config);
        setSettingOpen(true);
    };

    const saveSetting = async () => {
        const values = await form.validateFields();
        try {
            const saved = await request<StatisticsConfig>("update", {
                host: values.host || "",
                statisticsRetentionDays: String(values.retentionDays || 30),
            });
            setConfig(saved);
            await refreshPage();
            messageApi.success("已保存");
            setSettingOpen(false);
        } catch (e) {
            messageApi.error(e instanceof Error ? e.message : "保存失败");
        }
    };

    const columns = useMemo<ColumnsType<StatisticsLogRow>>(() => [
        {
            title: "访问时间",
            dataIndex: "time",
            width: 132,
        },
        {
            title: "文章",
            dataIndex: "alias",
            width: 180,
            render: (value: string) => <Typography.Text strong ellipsis>{value}</Typography.Text>,
        },
        {
            title: "路径",
            dataIndex: "path",
            render: (value: string) => (
                <Tooltip title={value}>
                    <span>{ellipsis(value, 36)}</span>
                </Tooltip>
            ),
        },
        {
            title: "来源",
            dataIndex: "source",
            width: 104,
            render: (value: string) => <Tag>{value}</Tag>,
        },
        {
            title: "设备",
            dataIndex: "deviceType",
            width: 168,
            render: (_: string, row) => (
                <Flex align="center" gap={6} style={{ whiteSpace: "nowrap" }}>
                    <Tag color={row.deviceType === "移动端" ? "blue" : undefined}>{row.deviceType}</Tag>
                    <Typography.Text type="secondary">{row.windowWidth ? `${row.windowWidth}px` : "未知"}</Typography.Text>
                </Flex>
            ),
        },
        {
            title: "访客",
            dataIndex: "visitorId",
            width: 100,
            render: (value: string) => <Typography.Text code>{shortId(value)}</Typography.Text>,
        },
        {
            title: "IP",
            dataIndex: "ip",
            width: 132,
            render: (value: string) => value || "-",
        },
        {
            title: "方法",
            dataIndex: "method",
            width: 82,
            render: (value: string) => value || "-",
        },
        {
            title: "详情",
            key: "action",
            width: 84,
            render: (_, row) => <Button size="small" onClick={() => setDetail(row)}>查看</Button>,
        },
    ], []);

    const emptyDescription = (
        <span>暂无访问数据，可能是统计代码还没有在管理后台的其他设置中生效，也可能当前还没有访问记录。</span>
    );

    return (
        <div
            style={{
                width: "100%",
                maxWidth: 1240,
                margin: "0 auto",
                padding: screens.xs ? 14 : 20,
                boxSizing: "border-box",
            }}
        >
            {contextHolder}
            <Flex
                justify="space-between"
                align="flex-start"
                gap={16}
                vertical={screens.xs}
                style={{ marginBottom: 18 }}
            >
                <div>
                    <Typography.Title level={2} style={{ margin: 0, fontSize: 24, lineHeight: "32px", fontWeight: 650 }}>
                        访问统计
                    </Typography.Title>
                    <Typography.Text type="secondary" style={{ marginTop: 6, display: "block", fontSize: 14 }}>
                        最近 {config.retentionDays || 30} 天访问明细和趋势
                    </Typography.Text>
                </div>
                <Space wrap style={{ marginTop: screens.xs ? 12 : 0 }}>
                    <Button icon={<ReloadOutlined/>} onClick={refreshPage} loading={loading}>刷新</Button>
                    <Button icon={<SettingOutlined/>} onClick={openSetting}>设置</Button>
                </Space>
            </Flex>

            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
                {metrics.map(metric => (
                    <Col xs={24} sm={12} md={6} key={metric.label}>
                        <Card
                            bordered
                            style={{
                                position: "relative",
                                minHeight: 92,
                                borderColor: token.colorBorderSecondary,
                                borderRadius: token.borderRadiusLG,
                                backgroundColor: token.colorBgContainer,
                            }}
                            bodyStyle={{ padding: 16 }}
                        >
                            <Statistic title={metric.label} value={metric.value}/>
                            <Tag
                                color={statusColor(metric.status)}
                                style={{
                                    position: "absolute",
                                    top: 14,
                                    right: 12,
                                    marginRight: 0,
                                }}
                            >
                                {metric.status === "processing" ? "今日有访问" : "统计中"}
                            </Tag>
                        </Card>
                    </Col>
                ))}
            </Row>

            {logs.total === 0 && (
                <Alert
                    style={{ marginBottom: 16 }}
                    type="info"
                    showIcon
                    message="还没有统计数据"
                    description={emptyDescription}
                    action={<Button size="small" onClick={openSetting}>查看接入代码</Button>}
                />
            )}

            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
                {charts.map(chart => (
                    <Col
                        xs={24}
                        sm={24}
                        md={chart.type === "line" ? 24 : 12}
                        key={chart.title}
                    >
                        <Card
                            bordered
                            style={{
                                minWidth: 0,
                                height: "100%",
                                borderColor: token.colorBorderSecondary,
                                borderRadius: token.borderRadiusLG,
                                backgroundColor: token.colorBgContainer,
                            }}
                            bodyStyle={{ padding: 16 }}
                        >
                            <div style={{ marginBottom: 12, fontSize: 15, fontWeight: 600, color: token.colorTextHeading }}>
                                {chart.title}
                            </div>
                            <ChartBlock chart={chart} colorPrimary={data.colorPrimary || token.colorPrimary}/>
                        </Card>
                    </Col>
                ))}
            </Row>

            <Card
                bordered
                style={{
                    borderColor: token.colorBorderSecondary,
                    borderRadius: token.borderRadiusLG,
                    backgroundColor: token.colorBgContainer,
                }}
                bodyStyle={{ padding: 16 }}
            >
                <Flex
                    justify="space-between"
                    align="center"
                    gap={12}
                    vertical={screens.xs}
                    style={{ marginBottom: 14 }}
                >
                    <Space wrap style={{ marginTop: screens.xs ? 12 : 0 }}>
                        <Input.Search
                            allowClear
                            placeholder="搜索路径、来源、IP"
                            onSearch={value => {
                                const next = {...filters, keyword: value};
                                setFilters(next);
                                loadLogs(1, logs.pageSize, next);
                            }}
                            style={{width: 240}}
                        />
                        <Input
                            allowClear
                            placeholder="文章 alias"
                            onChange={event => {
                                const next = {...filters, alias: event.target.value};
                                setFilters(next);
                            }}
                            onPressEnter={() => loadLogs(1, logs.pageSize)}
                            style={{width: 180}}
                        />
                        <Select
                            value={filters.source || ""}
                            options={sourceOptions}
                            onChange={value => {
                                const next = {...filters, source: value};
                                setFilters(next);
                                loadLogs(1, logs.pageSize, next);
                            }}
                            style={{width: 136}}
                        />
                    </Space>
                    <Typography.Text type="secondary">共 {logs.total} 条</Typography.Text>
                </Flex>
                <Table
                    rowKey="id"
                    size="middle"
                    loading={loading}
                    columns={columns}
                    dataSource={logs.rows}
                    scroll={{x: 1180}}
                    locale={{emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription}/>}}
                    pagination={{
                        current: logs.page,
                        pageSize: logs.pageSize,
                        total: logs.total,
                        showSizeChanger: true,
                    }}
                    onChange={pagination => loadLogs(pagination.current || 1, pagination.pageSize || 10)}
                />
            </Card>

            <Drawer
                title="统计设置"
                open={settingOpen}
                width={420}
                onClose={() => setSettingOpen(false)}
                extra={<Button type="primary" onClick={saveSetting}>保存</Button>}
            >
                <Form form={form} layout="vertical">
                    <Form.Item label="访问统计 Host" name="host">
                        <Input placeholder="例如 static.example.com"/>
                    </Form.Item>
                    <Form.Item label="统计窗口" name="retentionDays">
                        <Select options={retentionOptions}/>
                    </Form.Item>
                </Form>
                <div style={{ marginTop: 22 }}>
                    <Typography.Text style={{ marginBottom: 8, display: "block", fontSize: 14, fontWeight: 600 }}>
                        插件代码
                    </Typography.Text>
                    <Input.TextArea readOnly value={embedCode} rows={2}/>
                    <Button
                        style={{ marginTop: 8 }}
                        icon={<CopyOutlined/>}
                        onClick={() => {
                            navigator.clipboard?.writeText(embedCode);
                            messageApi.success("已复制");
                        }}
                    >
                        复制
                    </Button>
                </div>
                <div style={{ marginTop: 22 }}>
                    <Typography.Text style={{ marginBottom: 8, display: "block", fontSize: 14, fontWeight: 600 }}>
                        预览
                    </Typography.Text>
                    <iframe
                        title="statistics-widget"
                        src="/p/statistics/widget?preview=true"
                        style={{
                            width: "100%",
                            height: 56,
                            border: `1px solid ${token.colorBorderSecondary}`,
                            borderRadius: token.borderRadiusLG,
                        }}
                    />
                </div>
            </Drawer>

            <Drawer
                title="访问详情"
                open={detail !== null}
                width={560}
                onClose={() => setDetail(null)}
            >
                {detail && (
                    <Descriptions column={1} size="small" bordered>
                        <Descriptions.Item label="访问时间">{detail.time}</Descriptions.Item>
                        <Descriptions.Item label="文章">{detail.alias}</Descriptions.Item>
                        <Descriptions.Item label="路径">{detail.path || "-"}</Descriptions.Item>
                        <Descriptions.Item label="URL">{detail.url || "-"}</Descriptions.Item>
                        <Descriptions.Item label="来源">{detail.source}</Descriptions.Item>
                        <Descriptions.Item label="Referer">{detail.referer || "-"}</Descriptions.Item>
                        <Descriptions.Item label="IP">{detail.ip || "-"}</Descriptions.Item>
                        <Descriptions.Item label="访客 ID">{detail.visitorId || "-"}</Descriptions.Item>
                        <Descriptions.Item label="Session ID">{detail.sessionId || "-"}</Descriptions.Item>
                        <Descriptions.Item label="设备">{detail.deviceType || "-"}</Descriptions.Item>
                        <Descriptions.Item label="视口">{displayViewport(detail)}</Descriptions.Item>
                        <Descriptions.Item label="宽度区间">{detail.viewportRange || "-"}</Descriptions.Item>
                        <Descriptions.Item label="浏览器">{detail.browser || "-"}</Descriptions.Item>
                        <Descriptions.Item label="系统">{detail.os || "-"}</Descriptions.Item>
                        <Descriptions.Item label="方法">{detail.method || "-"}</Descriptions.Item>
                        <Descriptions.Item label="语言">{detail.language || "-"}</Descriptions.Item>
                        <Descriptions.Item label="User-Agent">{detail.userAgent || "-"}</Descriptions.Item>
                    </Descriptions>
                )}
            </Drawer>
        </div>
    );
};

export default StatisticsIndex;
