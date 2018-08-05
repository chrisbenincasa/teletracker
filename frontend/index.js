import './App/Config';
import './App/Config/ReactotronConfig';

import _ from 'lodash';
import * as React from 'react';
import { AppRegistry, Dimensions } from 'react-native';
import SideMenu from 'react-native-side-menu';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';

import MenuScreen from './App/Containers/MenuScreen';
import { Nav } from './App/Navigation/AppNavigation';
import NavigationService from './App/Navigation/NavigationService';
import createStore from './App/Redux';

import { Provider as PaperProvider } from 'react-native-paper';

const { store, persistor } = createStore();

class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            drawerDisabled: true
        }
    }

    handleNavigationRef(navRef) {
        NavigationService.setTopLevelNavigator(navRef);
    }

    handleNavigationStateChange(prevState, currentState) {
        let disabled = true;

        if (_.isObject(Nav.router.getComponentForState(currentState).drawerOptions)) {
            let opts = Nav.router.getComponentForState(currentState).drawerOptions;
            disabled = opts.hasOwnProperty('enabled') ? !opts.enabled : true;
        }
        
        this.setState({ drawerDisabled: disabled });
    }

    screenWidth = Dimensions.get('window');

    render() {
        return (
            <Provider store={store}>
            <PaperProvider>
                <PersistGate loading={null} persistor={persistor}>
                    <SideMenu 
                        menu={<MenuScreen />} 
                        openMenuOffset={this.screenWidth.width * 0.75}
                        disableGestures={this.state.drawerDisabled}>
                        <Nav 
                            ref={navRef => this.handleNavigationRef(navRef)} 
                            onNavigationStateChange={(prevState, currentState) => this.handleNavigationStateChange(prevState, currentState)}/>
                    </SideMenu>
                </PersistGate>
                </PaperProvider>
            </Provider>
        );
    }
}

AppRegistry.registerComponent('teletracker', () => App);
