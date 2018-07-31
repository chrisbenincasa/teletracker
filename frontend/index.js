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
        if (Nav.router.getComponentForState(currentState).navigationOptions) {
            let drawerOptions;
            if (_.isFunction(Nav.router.getComponentForState(currentState).navigationOptions)) {
                drawerOptions = (Nav.router.getComponentForState(currentState).navigationOptions(NavigationService.navigator()) || {}).drawer || {};
            } else {
                drawerOptions = Nav.router.getComponentForState(currentState).navigationOptions.drawer || {};
            }    

            disabled = drawerOptions.hasOwnProperty('enabled') ? !drawerOptions.enabled : true;
        }
        
        this.setState({ drawerDisabled: disabled });
    }

    screenWidth = Dimensions.get('window');

    render() {
        return (
            <Provider store={store}>
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
            </Provider>
        );
    }
}

AppRegistry.registerComponent('teletracker', () => App);
