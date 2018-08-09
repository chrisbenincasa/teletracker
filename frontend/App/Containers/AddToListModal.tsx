import _ from 'lodash';
import React from 'react';
import { Text, View } from 'react-native';
import { Checkbox, ListItem  } from 'react-native-paper'

import { NavigationScreenProp } from 'react-navigation';
import { connect, Dispatch } from 'react-redux';

import TouchableItem from '../Components/TouchableIItem';
import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import { State as ReduxState } from '../Redux/State';
import { UserState } from '../Redux/UserRedux';
import styles from './Styles/ItemDetailScreenStyle';
import { tracker } from '../Components/Analytics';


type Props = {
    user: UserState
    componentId: string
    thing: Thing
    userDetails: { belongsToLists: any[] }
    listActionInProgress: boolean
    getRef: (r: any) => any,
    updateLists: (thing: Thing, listsToAdd: string[], listsToRemove: string[]) => any
    navigation: NavigationScreenProp<any>
}

type ChosenMap = { [s: string]: boolean }

type State = {
    initial: ChosenMap
    chosen: ChosenMap
}

class AddToListModal extends React.PureComponent<Props, State> {
    static navigationOptions = ({navigation}: { navigation: NavigationScreenProp<any> }) => {
        return {
            headerBackTitle: 'Cancel',
            headerLeft: (
                <TouchableItem
                    accessibilityComponentType="button"
                    accessibilityLabel='Cancel'
                    accessibilityTraits="button"
                    testID="header-back"
                    delayPressIn={0}
                    onPress={() => navigation.goBack()}
                    pressColor='rgba(0, 0, 0, .32)'
                    style={{
                        alignItems: 'center',
                        flexDirection: 'row',
                        backgroundColor: 'transparent'
                    }}
                    borderless
                >
                    <Text 
                        style={{ fontSize: 17, fontWeight: 'normal', marginHorizontal: 10, color: 'white', textAlign: 'right' }}
                        onPress={() => navigation.goBack()}
                    >Cancel</Text>
                </TouchableItem>
            ),
            headerRight: (
                <Text
                    style={{ fontSize: 17, fontWeight: 'normal', marginHorizontal: 10, color: 'white', textAlign: 'right' }}
                    onPress={() => navigation.getParam('navigationButtonPressed')('doneButton')}
                >
                    Done
                    </Text>
            ),
            title: 'Manage Tracking'
        }
    }

    constructor(props: Props) {
        super(props);
        let belongsToIds = _.map(this.props.userDetails.belongsToLists, 'id');
        let [tracks, notTracks] = _.partition(this.props.user.details.lists, list => _.includes(belongsToIds, list.id));

        let initial = tracks.map(l => {
            return { [l.id.toString()]: true };
        }).concat(notTracks.map(l => {
            return { [l.id.toString()]: false }
        })).reduce((acc, i) => {
            return { ...acc, ...i }
        }, {});

        this.state = {
            initial,
            chosen: initial
        }
    }

    navigationButtonPressed(buttonId: string) {
        if (buttonId == 'backButton') {
            this.props.navigation.pop();
        } else if (buttonId == 'doneButton') {
            let keys = Object.keys(this.state.chosen);
            let removed: string[] = [], added: string[] = [];
            for (let i = 0; i < keys.length; i++) {
                let initial = this.state.initial[keys[i]];
                let changed = this.state.chosen[keys[i]];

                if (!initial && changed) {
                    added.push(keys[i]);
                } else if (initial && !changed) {
                    removed.push(keys[i]);
                }
            }

            this.props.updateLists(this.props.thing, added, removed);
        }
    }

    componentDidUpdate(prevProps: Props, prevState: State) {
        if (prevProps.listActionInProgress && !this.props.listActionInProgress) {
            this.props.navigation.pop();
        }
    }

    componentDidMount() {
        tracker.trackScreenView('AddToListModal');
        this.props.navigation.setParams({ navigationButtonPressed: this.navigationButtonPressed.bind(this) });
    }

    handleCheckboxPress(listId: number) {
        this.setState({
            chosen: {
                ...this.state.chosen,
                [listId.toString()]: this.state.chosen[listId.toString()] ? false : true
            }
        });
    }

    render() {
        return (
            <View style={styles.container}>
                {this.props.user.details.lists.map((list, i) => (
                    <View key={i}>
                        <ListItem 
                            title={list.name} 
                            avatar={
                                <View style={{
                                    borderColor: '#000',
                                    borderWidth: 1,
                                    borderStyle: 'solid'
                                }}>
                                    <Checkbox
                                        containerStyle={{
                                            backgroundColor: 'transparent',
                                            margin: 0,
                                            padding: 0,
                                            width: 24
                                        }}
                                        checked={this.state.chosen[list.id]}
                                        // right={true}
                                        // iconRight={true}
                                        onPress={() => this.handleCheckboxPress(list.id)}
                                    />
                                </View>
                            }
                        />
                    </View>
                ))}
            </View>
        );
    }
}

const mapStateToProps = (state: ReduxState) => {
    return {
        user: state.user,
        listActionInProgress: state.lists.actionInProgress
    };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        updateLists: (thing: Thing, listsToAdd: string[], listsToRemove: string[]): any => {
            dispatch(ListActions.updateListTracking(thing, listsToAdd, listsToRemove));
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(AddToListModal);