// Shim from https://github.com/ptelad/react-native-iphone-x-helper/blob/master/index.js

import { Dimensions, Platform, StatusBar } from 'react-native';

export function isIphoneX() {
    const dimen = Dimensions.get('window');
    return (
        Platform.OS === 'ios' &&
        !Platform.isPad &&
        !Platform.isTVOS &&
        (dimen.height === 812 || dimen.width === 812)
    );
}

export function ifIphoneX<T, U>(iphoneXStyle: T, regularStyle: U) {
    if (isIphoneX()) {
        return iphoneXStyle;
    }
    return regularStyle;
}

export function getStatusBarHeight(safe?: boolean) {
    return Platform.select({
        ios: ifIphoneX(safe ? 44 : 30, 20),
        android: StatusBar.currentHeight
    });
}