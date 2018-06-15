import startNav from './App/Navigation/AppNavigation';
import createStore from './App/Redux';

require('./App/Config/ReactotronConfig');

const store = createStore();
startNav(store);
