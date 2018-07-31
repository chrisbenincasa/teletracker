import {View, Text} from 'react-native';
import { State as ReduxState } from "../Redux/State";
import { Dispatch, connect } from 'react-redux'
import React from "react";
import { Navigation } from 'react-native-navigation';
import { UserState } from '../Redux/UserRedux';
import { ListItem, CheckBox } from 'react-native-elements';
import _ from 'lodash';
import ListActions from '../Redux/ListRedux';
import { Thing } from '../Model/external/themoviedb';
import { access } from 'fs';

type Props = {
    user: UserState
    componentId: string
    thing: Thing
    userDetails: { belongsToLists: any[] }
    listActionInProgress: boolean
    getRef: (r: any) => any,
    updateLists: (thing: Thing, listsToAdd: string[], listsToRemove: string[]) => any
}

type ChosenMap = { [s: string]: boolean }

type State = {
    initial: ChosenMap
    chosen: ChosenMap
}

class AddToListModal extends React.PureComponent<Props, State> {
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

        Navigation.events().bindComponent(this);
    }

    navigationButtonPressed({buttonId}: any) {
        if (buttonId == 'backButton') {
            Navigation.dismissModal(this.props.componentId);
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
            Navigation.dismissModal(this.props.componentId);
        }
    }

    componentDidMount() {
        // this.updateTopButtons();
    }

    updateTopButtons() {
        let anySelected = _.reduce(this.state.chosen, (acc, v) => acc || v, false);

        Navigation.mergeOptions(this.props.componentId, {
            topBar: {
                rightButtons: [{
                    id: 'doneButton',
                    text: 'Done',
                    enabled: anySelected
                }]
            }
        })
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
            <View>
                {this.props.user.details.lists.map((list, i) => (
                    <View key={i}>
                        <ListItem 
                            title={list.name} 
                            rightIcon={
                            <CheckBox
                                containerStyle={{
                                    backgroundColor: 'none',
                                    margin: 0,
                                    marginRight: 0,
                                    marginLeft: 0,
                                    padding: 0,
                                    borderWidth: 0,
                                    width: 24
                                }}
                                checked={this.state.chosen[list.id]}
                                right={true}
                                iconRight={true}
                                onIconPress={() => this.handleCheckboxPress(list.id)}
                            />
                        }/>
                        
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

export const AddToListModalOptions = {
    topBar: {
        title: {
            text: 'Add to Lists'
        },
        leftButtons: [{
            id: 'backButton',
            text: 'Cancel'
        }],
        rightButtons: [{
            id: 'doneButton',
            text: 'Done',
            enabled: true
        }],
        visible: true
    }
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(AddToListModal);