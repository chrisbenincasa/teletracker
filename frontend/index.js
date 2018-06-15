import startNav from './App/Navigation/AppNavigation';
import createStore from './App/Redux';

require('./App/Config/ReactotronConfig');
// AppRegistry.registerComponent('teletracker', () => App)
// create our store
const store = createStore();
startNav(store);
