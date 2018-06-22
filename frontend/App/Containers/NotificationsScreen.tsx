import React, { Component } from 'react';
import { View, Text, KeyboardAvoidingView } from 'react-native';
import { connect, Dispatch } from 'react-redux';
import { Card, ListItem, Icon } from 'react-native-elements';
import Header from '../Components/Header/Header';

import UserActions, { UserState } from '../Redux/UserRedux';

// Styles
import styles from './Styles/NotificationsScreenStyle';

interface Props {
    componentId: string
    user: UserState
    loadUserSelf: (componentId: string) => any
}

class NotificationsScreen extends Component<Props> {

    state = {};

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    render() {
        return (
            <View style={styles.container}>
                <Header 
                    title="Search" 
                    componentId={this.props.componentId} 
                    centerComponent={{title: 'Notifications',  style: { color: 'white' } }} 
                />
                <Text>Notifications Screen</Text>
            </View>
        );
    }
}

const mapStateToProps = state => {
    return {};
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(NotificationsScreen);
