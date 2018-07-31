import { NavigationState } from "react-navigation";

export function getCurrentRouteName(navigationState: NavigationState): string {
    if (!navigationState) return null;

    const route = navigationState.routes[navigationState.index];

    if (route.routes) {
        return getCurrentRouteName(route);
    } else {
        return route.routeName;
    }
}