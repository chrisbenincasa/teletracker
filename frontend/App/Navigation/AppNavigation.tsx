import R from 'ramda';
import * as React from 'react';
import { Navigation } from 'react-native-navigation';
import { Provider } from 'react-redux';
import { Store } from 'redux';
import { Persistor } from 'redux-persist';
import { PersistGate } from 'redux-persist/integration/react';

import ItemList from '../Containers/ItemList';
import LoginScreen from '../Containers/LoginScreen';
import SignupScreen from '../Containers/SignupScreen';
import SplashScreen from '../Containers/SplashScreen';
import { State } from '../Redux/State';
import ItemDetailScreen from '../Containers/ItemDetailScreen';

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
                <Scene ref="child" {...this.props}/>
              </PersistGate>
            </Provider>
          )
      }
    }
  }
}

export default function startNav(store: Store<State>, persistor: Persistor) {
  Navigation.registerComponent('navigation.main.Loading', sceneCreator(SplashScreen, store, persistor))
  Navigation.registerComponent('navigation.main.LoginScreen', sceneCreator(LoginScreen, store, persistor));
  Navigation.registerComponent('navigation.main.SignupScreen', sceneCreator(SignupScreen, store, persistor));
  Navigation.registerComponent('navigation.main.ListView', sceneCreator(ItemList, store, persistor));
  Navigation.registerComponent('navigation.main.DetailView', sceneCreator(ItemDetailScreen, store, persistor));

  Navigation.events().registerAppLaunchedListener(() => {
    Navigation.setDefaultOptions({
      animations: {
        startApp: {
          x: {
            from: 1000,
            to: 0,
            duration: 500,
            interpolation: 'accelerate',
          },
          alpha: {
            from: 0,
            to: 1,
            duration: 500,
            interpolation: 'accelerate'
          }
        }
      }
    });

    Navigation.setRoot({
      root: {
        stack: {
          id: 'Login',
          children: [
            {
              component: {
                name: 'navigation.main.Loading',
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