import moment from 'moment';
import R from 'ramda';
import React, { Component } from 'react';
import { View } from 'react-native';
import { List, ListItem } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { connect, Dispatch } from 'react-redux';

import Header from '../Components/Header/Header';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import EventActions, { EventsState } from '../Redux/EventsRedux';
import State from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import { retrieveEvents } from '../Sagas/EventSagas';
import styles from './Styles/NotificationsScreenStyle';

// Styles
interface Props {
    componentId: string
    user: UserState
    events: EventsState
    loadUserSelf: (componentId: string) => any,
    retrieveEvents: () => any
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

// type DefaultProps = Readonly<Partial<Props>>

class NotificationsScreen extends Component<Props> {
    // static defaultProps = defaultProps;

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
        this.props.retrieveEvents();
    }

    getSubtitle(event: any): string {
        if (event.type == 'MarkedAsWatched') {
            return `Watched ${moment(event.timestamp).local().fromNow()}`;
        } else {
            return;
        }
    }

    goToDetailView(event: any): void {
        const view = R.mergeDeepLeft(NavigationConfig.DetailView, {
            component: {
                passProps: { itemType: event.targetEntityType.toLowerCase(), itemId: event.targetEntityId }
            }
        });
        
        Navigation.push(this.props.componentId, view);
    }

    render() {
        return (
            <View style={styles.container}>
                <Header 
                    title="Search" 
                    componentId={this.props.componentId}
                    centerComponent={{title: 'Feed' ,style: { color: 'white' } }}
                />
                <List>
                {
                    this.props.events.loadedEvents.map((event, i) => (
                        <ListItem
                            key={event.id}
                            title={event.targetEntity.name}
                            subtitle={this.getSubtitle(event)}
                            subtitleNumberOfLines={2}
                            leftIcon={{ type: 'material-community', name: 'sunglasses'}}
                            hideChevron={true}
                            onPress={() => this.goToDetailView(event)}
                        />
                    ))
                }
                </List>
            </View>
        );
    }
}

const mapStateToProps = (state: State) => {
    return {
        events: state.events
    };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        },
        retrieveEvents: () => {
            dispatch(EventActions.retrieveEvents())
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(NotificationsScreen);
