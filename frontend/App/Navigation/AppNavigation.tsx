import * as React from 'react';
import { Icon } from 'react-native-elements';
import {
    createBottomTabNavigator,
    createDrawerNavigator,
    createStackNavigator,
    createSwitchNavigator,
    NavigationScreenConfig,
    NavigationScreenOptions,
} from 'react-navigation';
import { createFluidNavigator } from 'react-navigation-fluid-transitions';
import AddToListModal from '../Containers/AddToListModal';
import CreateNewListModal from '../Containers/CreateNewListModal';
import ItemDetailScreen from '../Containers/ItemDetailScreen';
import ItemList from '../Containers/ItemList';
import ListDetailScreen from '../Containers/ListDetailScreen';
import LoadingScreen from '../Containers/LoadingScreen';
import LoginScreen from '../Containers/LoginScreen';
import MenuScreen from '../Containers/MenuScreen';
import NotificationsScreen from '../Containers/NotificationsScreen';
import SearchScreen from '../Containers/SearchScreen';
import SignupScreen from '../Containers/SignupScreen';
import Drawer from 'react-native-drawer'
import Colors from '../Themes/Colors';

const paramsToProps = (SomeComponent) => {
    // turns this.props.navigation.state.params into this.params.<x>
    return class extends React.Component {
        static drawerOptions = SomeComponent.drawerOptions;
        static navigationOptions = SomeComponent.navigationOptions;
        // everything else, call as SomeComponent
        render() {
            const { navigation, ...otherProps } = this.props
            const { state: { params } } = navigation
            return <SomeComponent {...this.props} {...params} />
        }
    }
}

export const AuthStack = createStackNavigator({
    Login: {
        screen: paramsToProps(LoginScreen)
    },
    Signup: {
        screen: paramsToProps(SignupScreen)
    }
}, {
    headerMode: 'none',
    mode: 'card'
});

export const CommonStackStyles: Partial<NavigationScreenConfig<NavigationScreenOptions>> = {
    headerStyle: {
        backgroundColor: Colors.headerBackground,
    },
    headerTintColor: 'white',
    headerBackTitleStyle: {
        color: 'white'
    },
    headerTitleStyle: {
        color: 'white',
        fontWeight: 'normal'
    }
}

const ItemDetailStack = createStackNavigator({
    DetailScreen: {
        screen: paramsToProps(ItemDetailScreen)
    },
    ListManage: {
        screen: paramsToProps(AddToListModal),
    }
}, {
    mode: 'modal',
    navigationOptions: ({navigation}) => {
        let tabBarVisible = true;
        if (navigation.state.index > 0) {
            tabBarVisible = false;
        }

        return {
            ...CommonStackStyles,
            tabBarVisible,
        };
    }
});

export const ListStack = createStackNavigator({
    ListOfLists: {
        screen: paramsToProps(ListDetailScreen)
    },
    SpecificList: {
        screen: paramsToProps(ItemList)
    },
    DetailScreen: {
        screen: ItemDetailStack,
        navigationOptions: {
            header: null,
        }
    }
}, {
        navigationOptions: {
            ...CommonStackStyles
        }
    }
);

ItemDetailStack.navigationOptions = ({navigation}) => {
    let tabBarVisible = true;
    if (navigation.state.index > 0) {
        tabBarVisible = false;
    }

    return {
        tabBarVisible,
    }
}

const SearchStack = createStackNavigator({
    Search: {
        screen: paramsToProps(SearchScreen)
    },
    DetailScreen: {
        screen: ItemDetailStack,
        navigationOptions: {
            header: null,
        }
    },
    ListManage: {
        screen: paramsToProps(AddToListModal),
    }
}, {
    initialRouteName: 'Search',
    navigationOptions: {
        ...CommonStackStyles
    }
});

const NotificationsStack = createStackNavigator({
    Notifications: {
        screen: paramsToProps(NotificationsScreen)
    }
}, {
    navigationOptions: {
        ...CommonStackStyles,
    }
});

// export const ListOfListsNav = createDrawerNavigator({
//     Main: ListStack,
//     SettingsDrawer: {
//         screen: MenuScreen
//     }
// });

// ListOfListsNav.navigationOptions = {
//     headerStyle: {
//         backgroundColor: Colors.headerBackground,
//     }
// }

export const AppStack = createBottomTabNavigator({
    'My Lists': ListStack,
    Search: SearchStack,
    Notifications: NotificationsStack
}, {
    initialRouteName: 'My Lists',
    navigationOptions: ({ navigation }) => ({
        tabBarIcon: ({ focused, tintColor }) => {
            const { routeName } = navigation.state;
            let iconName, iconType;
            if (routeName === 'My Lists') {
                iconName = 'list';
                iconType = 'entypo';
            } else if (routeName === 'Search') {
                iconName = 'search';
            } else if (routeName === 'Notifications') {
                iconName = 'notifications';
                iconType = 'material-icons'
            }

            return <Icon name={iconName} type={iconType} iconStyle={{ color: tintColor }} />
        },
        headerStyle: {
            backgroundColor: Colors.headerBackground,
        },
    })
});

export const AppModalStack = createStackNavigator({
    MainBottomTabs: {
        screen: AppStack,
        navigationOptions: {
            header: null
        }
    },
    CreateListModal: {
        screen: paramsToProps(CreateNewListModal)
    }
}, {
    mode: 'modal'
})

export const Nav = createSwitchNavigator({
    AuthLoading: {
        screen: paramsToProps(LoadingScreen)
    },
    Auth: AuthStack,
    App: AppModalStack
}, {
    initialRouteName: 'AuthLoading'
});