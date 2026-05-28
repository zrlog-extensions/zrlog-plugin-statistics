import {FunctionComponent} from "react";
import StatisticsIndex from "./components/StatisticsIndex";
import {StatisticsInfoResponse} from "./index";

export type AppBaseProps = {
    pluginInfo: StatisticsInfoResponse;
}

const AppBase: FunctionComponent<AppBaseProps> = ({pluginInfo}) => {
    return <StatisticsIndex data={pluginInfo}/>;
};

export default AppBase;
