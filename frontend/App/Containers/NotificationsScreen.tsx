import React, { Component } from 'react';
import { View, Text, KeyboardAvoidingView } from 'react-native';
import { connect, Dispatch } from 'react-redux';
import { Card, List, ListItem, Icon } from 'react-native-elements';
import Header from '../Components/Header/Header';

import UserActions, { UserState } from '../Redux/UserRedux';

// Styles
import styles from './Styles/NotificationsScreenStyle';

interface Props {
    componentId: string
    user: UserState
    loadUserSelf: (componentId: string) => any
}

const list = [
    {
        title: 'Ted 2',
        subtitle: 'Leaving Netflix in 2 days!',
        icon: 'flight-takeoff',
        badge: {
            value: '2 days', textStyle: { color: 'white' }
        }
    },
    {
        title: 'The Matrix',
        subtitle: 'Leaving HBO in 1 day',
        icon: 'flight-takeoff',
        badge: {
            value: '1 day', textStyle: { color: 'white' }
        }
    },
    {
        title: 'Patty Cake$',
        subtitle: 'Available on Hulu in 15 days',
        icon: 'av-timer',
        badge: {
            value: '15 days', textStyle: { color: 'white' }
        }
    },
];

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
                    centerComponent={{title: 'Notifications',style: { color: 'white' } }}
                />
                <List>
                {
                    list.map((item, i) => (
                        <ListItem
                            key={i}
                            title={item.title}
                            subtitle={item.subtitle}
                            subtitleNumberOfLines={2}
                            leftIcon={{name: item.icon}}
                            hideChevron={true}
                            badge={item.badge}
                        />
                      
                    ))
                }
                </List>
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
