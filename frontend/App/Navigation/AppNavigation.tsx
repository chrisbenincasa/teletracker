import * as React from 'react';
import { Navigation } from 'react-native-navigation';
import { Provider } from 'react-redux';
import { Store } from 'redux';
import { Persistor } from 'redux-persist';
import { PersistGate } from 'redux-persist/integration/react';

import ItemDetailScreen from '../Containers/ItemDetailScreen';
import ItemList from '../Containers/ItemList';
import ListDetailScreen from '../Containers/ListDetailScreen';
import LoginScreen from '../Containers/LoginScreen';
import MenuScreen from '../Containers/MenuScreen';
import NotificationsScreen from '../Containers/NotificationsScreen';
import SearchScreen from '../Containers/SearchScreen';
import SignupScreen from '../Containers/SignupScreen';
import SplashScreen from '../Containers/SplashScreen';
import { State } from '../Redux/State';
import Colors from '../Themes/Colors';
import AddToListModal from '../Containers/AddToListModal';
import CreateNewListModal from '../Containers/CreateNewListModal';
import { Icon } from 'react-native-elements';
import ModalHeaderButton from '../Components/ModalHeaderButton';


function sceneCreator(Scene: React.Component, store: Store<{}>, persistor: Persistor) {
    return () => {
        return class Wrapper extends React.Component {
            resendEvent(eventName: string, params?: any): void {
                if (this.instance && this.instance[eventName]) {
                    this.instance[eventName](params);
                }
            }

            componentDidAppear(): void {
                this.resendEvent('componentDidAppear');
            }

            componentDidDisappear(): void {
                this.resendEvent('componentDidDisappear');
            }

            onNavigationButtonPressed(buttonId: any): void {
                this.resendEvent('onNavigationButtonPressed', buttonId);
            }

            render() {
                return (
                    <Provider store={store}>
                        <PersistGate loading={null} persistor={persistor}>
                            <Scene ref="child" {...this.props} />
                        </PersistGate>
                    </Provider>
                )
            }
        }
    }
}

let appLaunchedListenerFired = false;

export function appLaunched() {
    return appLaunchedListenerFired;
}

class IconWrapper extends React.PureComponent {
    handlePress() {
        Navigation.dismissModal(this.props.getModalRef());
    }

    render() {
        return <Icon name='rowing' color={this.props.color || 'black'} onPress={() => this.handlePress()}/>
    }
}

export default function startNav(store: Store<State>, persistor: Persistor) {
    Navigation.registerComponent('navigation.main.Loading', sceneCreator(SplashScreen, store, persistor))
    Navigation.registerComponent('navigation.main.LoginScreen', sceneCreator(LoginScreen, store, persistor));
    Navigation.registerComponent('navigation.main.SignupScreen', sceneCreator(SignupScreen, store, persistor));
    Navigation.registerComponent('navigation.main.ListView', sceneCreator(ListDetailScreen, store, persistor));
    Navigation.registerComponent('navigation.main.ItemList', sceneCreator(ItemList, store, persistor));
    Navigation.registerComponent('navigation.main.ItemDetailScreen', sceneCreator(ItemDetailScreen, store, persistor));
    Navigation.registerComponent('navigation.main.MenuScreen', sceneCreator(MenuScreen, store, persistor));
    Navigation.registerComponent('navigation.main.SearchScreen', sceneCreator(SearchScreen, store, persistor));
    Navigation.registerComponent('navigation.main.NotificationsScreen', sceneCreator(NotificationsScreen, store, persistor));
    Navigation.registerComponent('navigation.main.AddToListModal', sceneCreator(AddToListModal, store, persistor));
    Navigation.registerComponent('navigation.main.CreateNewListModal', sceneCreator(CreateNewListModal, store, persistor));
    Navigation.registerComponent('navigation.topBar.Button', () => ModalHeaderButton);

    Navigation.events().registerCommandCompletedListener((event) => {
        console.log('Got Nav event: ', event);
    });

    Navigation.events().registerAppLaunchedListener(() => {
        Navigation.setDefaultOptions({
            statusBar: {
                style: 'light'
            },
            bottomTab: {
                selectedTextColor: Colors.headerBackground,
                selectedIconColor: Colors.headerBackground
            },
            topBar: {
                background: {
                    color: Colors.headerBackground
                },
                buttonColor: "rgba(255, 255, 255, 1.0)", // iOS
                title: {
                    color: 'white',
                },
                backButton: {
                    color: "rgba(255, 255, 255, 1.0)"
                }
            },
            sideMenu: {
                left: {
                    // enabled: false
                }
            }
        });

        appLaunchedListenerFired = true;

        store.dispatch({ type: 'navigation/registerAppLaunchedListener' });
    });
}