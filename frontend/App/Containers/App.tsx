import '../Config';

import React, { Component } from 'react';
import { Provider } from 'react-redux';

import DebugConfig from '../Config/DebugConfig';
import createStore from '../Redux';
import RootContainer from './RootContainer';

require('../Config/ReactotronConfig');
// const store = createStore()

class App extends Component {
  render () {
    return (
      // <Provider store={store}>
      // </Provider>
      <RootContainer />
    )
  }
}

// allow reactotron overlay for fast design in dev mode
export default DebugConfig.useReactotron
  ? console.tron.overlay(App)
  : App
