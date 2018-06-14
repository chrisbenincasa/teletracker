import { StackNavigator } from 'react-navigation';
import SettingsScreen from '../Containers/SettingsScreen'
import ItemDetailScreen from '../Containers/ItemDetailScreen'
import SignupScreen from '../Containers/SignupScreen'
import LoginScreen from '../Containers/LoginScreen'
import ItemList from '../Containers/ItemList';
import LaunchScreen from '../Containers/LaunchScreen';

import styles from './Styles/NavigationStyles';

// Manifest of possible screens
const PrimaryNav = StackNavigator({
  SettingsScreen: { screen: SettingsScreen },
  ItemDetailScreen: { screen: ItemDetailScreen },
  SignupScreen: { screen: SignupScreen },
  LoginScreen: { screen: LoginScreen },
  ItemList: { screen: ItemList },
  // This is ignite default, leaving for now for testing
  // LaunchScreen: { screen: LaunchScreen }
}, {
  // Default config for all screens
  headerMode: 'none',
  initialRouteName: 'LoginScreen',
  navigationOptions: {
    headerStyle: styles.header,
    gestureResponseDistance: {
      horizontal: 100 //default 25
    }
  }
});

export default PrimaryNav;
