import startNav from './App/Navigation/AppNavigation';
import createStore from './App/Redux';

require('./App/Config/ReactotronConfig');

// Uncomment for testing purposes only.
// import { AsyncStorage } from 'react-native';
// AsyncStorage.clear();

const { store, persistor } = createStore();
startNav(store, persistor);
