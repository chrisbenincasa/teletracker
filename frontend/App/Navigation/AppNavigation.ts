import { StackNavigator } from 'react-navigation';
import ItemList from '../Containers/ItemList';
import LaunchScreen from '../Containers/LaunchScreen';

import styles from './Styles/NavigationStyles';

// Manifest of possible screens
const PrimaryNav = StackNavigator({
  ItemList: { screen: ItemList },
  LaunchScreen: { screen: LaunchScreen }
}, {
  // Default config for all screens
  headerMode: 'none',
  initialRouteName: 'ItemList',
  navigationOptions: {
    headerStyle: styles.header
  }
});

export default PrimaryNav;
