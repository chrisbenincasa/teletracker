import React from 'react';
import { ConnectedRouter } from 'connected-react-router';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import App from '../src/containers/App';
// import './index.css';
import createStore, { history } from '../src/store';
import Amplify from '@aws-amplify/core';
import { launchUri } from '@aws-amplify/auth/lib/OAuth/urlOpener';

export default App;
