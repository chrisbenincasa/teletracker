import { View, Text } from 'react-native';
import { State as ReduxState } from "../Redux/State";
import { Dispatch, connect } from 'react-redux'
import React from "react";
import { Navigation } from 'react-native-navigation';
import { FormInput, FormLabel } from 'react-native-elements';
import ListActions, { ListState } from '../Redux/ListRedux';

type Props = {
    componentId: string,
    createList: (name: string) => any,
    list: ListState
}

type State = {
    name?: string,
    creating: boolean
}

class CreateNewListModal extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {
            creating: false
        }

        Navigation.events().bindComponent(this);
    }

    navigationButtonPressed({ buttonId }: any) {
        if (buttonId == 'backButton') {
            Navigation.dismissModal(this.props.componentId);
        } else if (buttonId == 'doneButton') {
            this.props.createList(this.state.name);
            this.setState(Object.assign(this.state, { creating: true }));
        }
    }

    componentDidUpdate(prevProps: Props, previousState: State) {
        this.updateTopButtons();

        if (prevProps.list.actionInProgress && !this.props.list.actionInProgress && previousState.creating) {
            Navigation.dismissModal(this.props.componentId);
        }
    }

    updateTopButtons() {
        let isActive = false;

        if (this.state.name && this.state.name.length > 0) {
            isActive = true;
        }

        Navigation.mergeOptions(this.props.componentId, {
            topBar: {
                rightButtons: [{
                    id: 'doneButton',
                    text: 'Done',
                    enabled: isActive
                }]
            }
        })
    }

    render() {
        return (
            <View>
                <FormLabel>Name</FormLabel>
                <FormInput
                    placeholder="Enter a List name"
                    onChangeText={(name) => this.setState({ name })} 
                />
            </View>
        );
    }
}

const mapStateToProps = (state: ReduxState) => {
    return {
        list: state.lists
    };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        createList: (name: string) => {
            dispatch(ListActions.createList(name))
        }
    };
};

const component = connect(
    mapStateToProps,
    mapDispatchToProps
)(CreateNewListModal);

const componentWithOptions = Object.assign(component, {
    options: {
        topBar: {
            title: {
                text: 'Create new List'
            },
            leftButtons: [{
                id: 'backButton',
                text: 'Cancel'
            }],
            rightButtons: [{
                id: 'doneButton',
                text: 'Done',
                enabled: false
            }],
            visible: true
        }
    }
});

export default componentWithOptions;