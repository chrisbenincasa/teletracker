import { NavigationActions, NavigationParams, NavigationNavigateAction, StackActions } from 'react-navigation';

let _navigator;

function setTopLevelNavigator(navigatorRef) {
    if (navigatorRef) {
        _navigator = navigatorRef;
    }
}

function navigate(routeName: string, params?: NavigationParams, action?: NavigationNavigateAction, key?: string) {
    _navigator.dispatch(
        NavigationActions.navigate({
            routeName,
            params,
            action,
            key,
        })
    );
}

function reset(actions: NavigationNavigateAction[]) {
    let action = StackActions.reset({
        index: 0,
        actions,
    });

    _navigator.dispatch(action);
}

// add other navigation functions that you need and export them

export default {
    navigator: () => _navigator,
    navigate,
    reset,
    setTopLevelNavigator,
};