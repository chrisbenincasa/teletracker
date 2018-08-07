import React, { Component } from 'react';
import { StatusBar, View, YellowBox } from 'react-native';
import { connect, Dispatch } from 'react-redux';
import { AnyAction } from 'redux';
import ReduxPersist from '../Config/ReduxPersist';
import StartupActions from '../Redux/StartupRedux';
import styles from './Styles/RootContainerStyles';

// Styles
class RootContainer extends Component<RootContainerProps> {
    componentDidMount () {
        // if redux persist is not active fire startup action
        if (!ReduxPersist.active) {
        this.props.startup()
        }
    }

    // <AppNavigation ref={navRef => NavigationService.setTopLevelNavigator(navRef)} />
    render () {
        return (
            <View style={styles.applicationView}>
                <StatusBar barStyle='light-content' />
            </View>
        )
    }
}

interface RootContainerProps {
  startup: () => AnyAction
}

// wraps dispatch to create nicer functions to call within our component
const mapDispatchToProps = (dispatch: Dispatch<any>) => ({
  startup: () => dispatch(StartupActions.startup())
})

export default connect(null, mapDispatchToProps)(RootContainer)
