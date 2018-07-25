import moment from 'moment';
import R from 'ramda';
import React, { Component } from 'react';
import { View, FlatList } from 'react-native';
import { List, ListItem } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { connect, Dispatch } from 'react-redux';

import Header from '../Components/Header/Header';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import EventActions, { EventsState } from '../Redux/EventsRedux';
import { State } from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
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

    keyExtractor: (item: any, index: any) => string = ({event}, index) => {
        return event.id
    }

    renderItem({item: { event, target }}) {
        return (
            <ListItem
                key={this.keyExtractor}
                title={target.name}
                subtitle={this.getSubtitle(event)}
                leftIcon={{ type: 'material-community', name: 'sunglasses' }}
                subtitleNumberOfLines={2}
                onPress={() => this.goToDetailView(event)}
                hideChevron={true}
            />
        )
    }

    render() {
        return (
            <View style={styles.container}>
                <Header
                    title="Feed"
                    componentId={this.props.componentId}
                    centerComponent={{ title: 'Feed', style: { color: 'white' } }}
                />
                <FlatList
                    keyExtractor={this.keyExtractor}
                    contentContainerStyle={styles.listContent}
                    renderItem={this.renderItem.bind(this)}
                    data={this.props.events.loadedEvents}
                />
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