// import { StackNavigator, SwitchNavigator } from 'react-navigation';
// import { Navigation } from 'react-native-navigation/lib/dist';
import * as React from 'react';
import { Provider } from 'react-redux';
import SettingsScreen from '../Containers/SettingsScreen'
import ItemDetailScreen from '../Containers/ItemDetailScreen'
import SignupScreen from '../Containers/SignupScreen'
import LoginScreen from '../Containers/LoginScreen'
import ItemList from '../Containers/ItemList';
import LaunchScreen from '../Containers/LaunchScreen';

import styles from './Styles/NavigationStyles';

import { Navigation } from 'react-native-navigation';
import App from '../Containers/App';

function sceneCreator(Scene, store) {
  return () => {
    return class Wrapper extends React.Component {
      constructor(props) {
        super(props)
        console.log(props);
      }
      render() {
        return (
            <Provider store={store}><Scene ref="child" {...this.props}/></Provider>
          )
      }
    }
  }
}

export default function startNav(store) {
  Navigation.registerComponent('navigation.main.LoginScreen', sceneCreator(LoginScreen, store));
  Navigation.registerComponent('navigation.main.SignupScreen', sceneCreator(SignupScreen, store));
  
  Navigation.events().registerAppLaunchedListener(() => {
    Navigation.setRoot({
      root: {
        stack: {
          id: 'Login',
          children: [
            {
              component: {
                name: 'navigation.main.LoginScreen',
                options: {
                  topBar: {
                    visible: false
                  }
                }
              }
            }
          ]
        }
      }
    })
  })
}


// // Manifest of possible screens
// const AppStack = StackNavigator({
//   SettingsScreen: { screen: SettingsScreen },
//   ItemDetailScreen: { screen: ItemDetailScreen },
//   ItemList: { screen: ItemList },
// }, {
//     navigationOptions: {
//       headerStyle: styles.header
//     }
// });

// const AuthStack = StackNavigator({
//   SignupScreen: { screen: SignupScreen },
//   LoginScreen: { screen: LoginScreen },
// }, {
//   headerMode: 'none',
//   navigationOptions: {
//     headerStyle: styles.header
//   }
// });

// const Main = SwitchNavigator({
//   LoginScreen: { screen: LoginScreen },
//   App: AppStack,
//   Auth: AuthStack
// }, {
//     // Default config for all screens
//     initialRouteName: 'LoginScreen'
//   });


// export default Main;