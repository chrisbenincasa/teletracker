import React, { isValidElement } from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { FormInput, FormLabel } from 'react-native-elements';
import { NavigationScreenProp } from 'react-navigation';
import { connect, Dispatch } from 'react-redux';
import { Snackbar } from 'react-native-paper'

import { CommonStackStyles } from '../Navigation/AppNavigation';
import ListActions, { ListState } from '../Redux/ListRedux';
import { State as ReduxState } from '../Redux/State';
import styles from './Styles/ItemDetailScreenStyle';

type Props = {
    createList: (name: string) => any,
    list: ListState,
    navigation: NavigationScreenProp<any>,
    visible: boolean
}

type State = {
    name?: string,
    creating: boolean,
    valid: boolean,
    visible: false
}

class CreateNewListModal extends React.PureComponent<Props, State> {
    static navigationOptions = ({ navigation }: { navigation: NavigationScreenProp<any> }) => {
        let doneEnabled: boolean = navigation.getParam('valid');
        let color = doneEnabled ? 'white' : 'rgba(255, 255, 255, 0.5)';

        return {
            title: 'Create a new List?',
            ...CommonStackStyles,
            headerLeft: (
                <TouchableOpacity style={{ marginHorizontal: 10 }}>
                    <Text
                        style={{
                            fontSize: 17,
                            fontWeight: 'normal',
                            marginHorizontal: 10,
                            color: 'white',
                            textAlign: 'right'
                        }}
                        onPress={() => navigation.goBack()}
                    >Cancel
                    </Text>
                </TouchableOpacity>
            ),
            headerRight: (
                <TouchableOpacity style={{ marginHorizontal: 10 }}>
                    <Text 
                        style={{
                            fontSize: 17,
                            fontWeight: 'normal',
                            marginHorizontal: 10,
                            color,
                            textAlign: 'right'
                        }}
                        onPress={() => doneEnabled ? navigation.getParam('navigationButtonPressed')('doneButton') : null}
                    >Create
                    </Text>
                </TouchableOpacity>
            )
        };
    }

    constructor(props: Props) {
        super(props);
        this.state = {
            creating: false,
            valid: false
        }
    }

    navigationButtonPressed({ buttonId }: any) {
        if (buttonId == 'backButton') {
            this.props.navigation.pop();
        } else if (buttonId == 'doneButton') {
            this.props.createList(this.state.name);
            this.setState(Object.assign(this.state, { creating: true }));
        }
    }

    componentDidMount() {
        this.props.navigation.setParams({ 
            navigationButtonPressed: this.navigationButtonPressed.bind(this), 
            doneButtonEnabled: this.doneButtonEnabled.bind(this) 
        });
    }

    componentDidUpdate(prevProps: Props, previousState: State) {
        if (prevProps.list.actionInProgress && !this.props.list.actionInProgress && previousState.creating) {
            this.props.navigation.goBack();
        }
    }

    doneButtonEnabled() {
        return this.state.valid;
    }

    updateFormState(name: string) {
        let isActive = false;

        if (name && name.length > 0) {
            isActive = true;
        }

        this.setState({
            name,
            valid: isActive,
            visible: !this.state.visible
        }, () => {
            this.props.navigation.setParams({ valid: isActive })
        });
    }

    render() {
        return (
            <View style={styles.container}>
                <FormLabel>Name</FormLabel>
                <FormInput
                    placeholder="Enter a List name"
                    onChangeText={(name) => this.updateFormState(name)} 
                />
                <Snackbar
                    visible={this.state.visible}
                    onDismiss={() => this.setState({ visible: false })}
                    action={{
                        label: 'Undo',
                        onPress: () => {

                        },
                    }}
                >
                    Item has been updated!
                </Snackbar>
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

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(CreateNewListModal);