import * as React from 'react';
import { Navigation } from 'react-native-navigation';
import { Provider } from 'react-redux';
import { Store } from 'redux';

import LoginScreen from '../Containers/LoginScreen';
import SignupScreen from '../Containers/SignupScreen';
import ItemList from '../Containers/ItemList';

function sceneCreator(Scene: React.Component, store: Store<{}>) {
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
            <Provider store={store}><Scene ref="child" {...this.props}/></Provider>
          )
      }
    }
  }
}

export default function startNav(store: Store<{}>) {
  Navigation.registerComponent('navigation.main.LoginScreen', sceneCreator(LoginScreen, store));
  Navigation.registerComponent('navigation.main.SignupScreen', sceneCreator(SignupScreen, store));

  Navigation.registerComponent('navigation.main.ListView', sceneCreator(ItemList, store));
  
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