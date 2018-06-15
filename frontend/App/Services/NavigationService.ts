import { NavigationActions, NavigationParams, NavigationContainer } from 'react-navigation';

let _navigator: NavigationContainer;

function setTopLevelNavigator(navigatorRef: NavigationContainer) {
    _navigator = navigatorRef;
}

function navigate(routeName: string, params?: NavigationParams) {
    console.log(_navigator);
    _navigator.dispatch(
        NavigationActions.navigate({
            routeName,
            params,
        })
    );
}

// add other navigation functions that you need and export them

export default {
    navigate,
    setTopLevelNavigator,
};