import { StackNavigator } from 'react-navigation';
import SettingsScreen from '../Containers/SettingsScreen';
import ItemDetailScreen from '../Containers/ItemDetailScreen';
import SignupScreen from '../Containers/SignupScreen';
import LoginScreen from '../Containers/LoginScreen';
import ItemList from '../Containers/ItemList';
import LaunchScreen from '../Containers/LaunchScreen';

import styles from './Styles/NavigationStyles';

// Manifest of possible screens
const AppStack = StackNavigator({
  SettingsScreen: { screen: SettingsScreen },
  ItemDetailScreen: { screen: ItemDetailScreen },
  ItemList: { screen: ItemList },
}, {
    navigationOptions: {
      headerStyle: styles.header
    }
});

const AuthStack = StackNavigator({
  SignupScreen: { screen: SignupScreen },
  LoginScreen: { screen: LoginScreen },
}, {
  headerMode: 'none',
  navigationOptions: {
    headerStyle: styles.header,
    gestureResponseDistance: {
      horizontal: 100 //default 25
    }
  }
});

const Main = SwitchNavigator({
  LoginScreen: { screen: LoginScreen },
  App: AppStack,
  Auth: AuthStack
}, {
    // Default config for all screens
    initialRouteName: 'LoginScreen'
  });


export default Main;