import React, { Component } from 'react';
import { ActivityIndicator, ScrollView, Text } from 'react-native';
import {
    Button,
    Card,
    TextInput
} from 'react-native-paper';
import { connect, Dispatch } from 'react-redux';

import { appVersion, tracker } from '../Components/Analytics';
import Logo from '../Components/Logo';
import { State as AppState } from '../Redux/State';
import UserActions, { SignupState } from '../Redux/UserRedux';
import styles from './Styles/SignupScreenStyle';

// Styles
interface State {
    username?: string,
    password?: string,
    email?: string
}

interface Props {
    signup: SignupState,
    navigation: any,
    onSignUpAttempt: (state: State) => any,
    onLoginPress: any
}

class SignupScreen extends Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { username: 'Christian', password: 'password', email: 'test@test.com' };
    }

    componentDidMount() {
        tracker.trackScreenView('Signup');
    }

    goToLogin() {
        tracker.trackEvent('signup-action', 'login', {
            label: appVersion
        });
        // Navigation.pop(this.props.componentId, {});
        this.props.navigation.goBack();
    }

    render() {
        return (
            <ScrollView
                style={styles.container}
                contentContainerStyle={{
                    flex: 1,
                    alignItems: 'center',
                    justifyContent: 'center'
                }}
            >
                <Logo />
                <Card style={{
                    margin: 15,
                    marginBottom: 100,
                    flexGrow: 0,
                    flexDirection: 'row'
                }}>
                    <TextInput
                        label='Username'
                        placeholder='Username...' 
                        onChangeText={(username) => this.setState({ username })} 
                        value={this.state.username}
                        editable={!this.props.signup.fetching}
                        autoCapitalize='none'
                        style={{margin: 5}}
                    />
                    <TextInput
                        label='Email'
                        placeholder='Email address...'
                        onChangeText={(email) => this.setState({ email })}
                        value={this.state.email}
                        editable={!this.props.signup.fetching}
                        autoCapitalize='none'
                        style={{margin: 5}}
                    />
                    <TextInput
                        label='Password'
                        secureTextEntry 
                        placeholder='Password...'
                        editable={!this.props.signup.fetching}
                        autoCapitalize='none'
                        onChangeText={(password) => this.setState({ password })}
                        value={this.state.password}
                        style={{margin: 5}}
                    />
                    <TextInput
                        label='Confirm Password'
                        secureTextEntry 
                        placeholder='Confirm Password...'
                        editable={!this.props.signup.fetching}
                        autoCapitalize='none'
                        onChangeText={(password) => this.setState({ password })}
                        value={this.state.password}
                        style={{margin: 5}}
                    />
                    <Text style={{textAlign: 'center', color: 'green' }}>
                        {this.props.signup.success ? 'Done!' : ''}
                    </Text>
                    <Text style={{ textAlign: 'center', color: 'red' }}>
                        {this.props.signup.error ? 'Something went wrong!' : ''}
                    </Text>
                    <Button
                        primary
                        raised
                        style={{ margin: 5 }}
                        onPress={() => this.props.onSignUpAttempt(this.state)} 
                    >
                        Sign Up
                    </Button>
                    <Button
                        style={{ margin: 5, color: '#bcbec1' }}
                        title='Login'
                        onPress={() => this.goToLogin()}
                    >
                        Login
                    </Button>
                    <ActivityIndicator animating={this.props.signup.fetching} />
                </Card>
            </ScrollView>
        )
    }
}

const mapStateToProps = (state: AppState) => {
    return {
        signup: state.user.signup
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        onSignUpAttempt: ({username, email, password}: State) => {
            dispatch(UserActions.userSignupRequest(username, email, password));
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(SignupScreen);
