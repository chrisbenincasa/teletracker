import React, { Component } from 'react';
import { ScrollView } from 'react-native';
import { Button, Card, FormInput, FormLabel } from 'react-native-elements';
import { NavigationScreenOptions, NavigationScreenProp } from 'react-navigation';
import { connect, Dispatch } from 'react-redux';

import { appVersion, tracker } from '../Components/Analytics';
import Logo from '../Components/Logo';
import { State as ReduxState } from '../Redux/State';
import UserActions from '../Redux/UserRedux';
import styles from './Styles/LoginScreenStyle';

// import { Navigation } from 'react-native-navigation';
type Props = {
    componentId: string,
    login: (componentId: string, email: string, password: string) => any,
    navigation: NavigationScreenProp<any>
}

type State = {
    email?: string,
    password?: string
}

class LoginScreen extends Component<Props, State> {
    private inputs: any

    static navigationOptions: NavigationScreenOptions = {
        // header: null
    }

    constructor(props: Props) {
        super(props);
        this.inputs = {};
    }

    componentDidMount() {
        tracker.trackScreenView('Login');
    }

    goToSignup() {
        tracker.trackEvent('login-action', 'signup', {
            label: appVersion
        });

        this.props.navigation.navigate('Signup');
    }

    render() {
        return (
            <ScrollView style={styles.container}>
                <Logo />
                <Card>
                    <FormLabel>Email</FormLabel>
                    <FormInput
                        placeholder="Email address..."
                        autoCapitalize='none'
                        keyboardType='email-address'
                        returnKeyType='next'
                        onSubmitEditing={() => this.inputs.password.focus()}
                        onChangeText={(email) => this.setState({ email })} />
                    <FormLabel>Password</FormLabel>
                    <FormInput
                        secureTextEntry
                        placeholder="Password..."
                        autoCapitalize='none'
                        ref={input => this.inputs.password = input}
                        onChangeText={(password) => this.setState({ password })} />

                    <Button
                        buttonStyle={{ marginTop: 20 }}
                        backgroundColor="#03A9F4"
                        title="Login"
                        onPress={() => this.props.login(this.props.componentId, this.state.email, this.state.password)}
                    />
                    <Button
                        buttonStyle={{ marginTop: 20 }}
                        backgroundColor="transparent"
                        textStyle={{ color: "#bcbec1" }}
                        onPress={this.goToSignup.bind(this)}
                        title="Sign Up"
                    />
                </Card>
            </ScrollView>
        )
    }
}

const mapStateToProps = (state: ReduxState) => {
    return {
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        login: (componentId: string, email: string, password: string) => {
            dispatch(UserActions.loginRequest(componentId, email, password));
        }
    };
}

export default connect(mapStateToProps, mapDispatchToProps, null, { withRef: true })(LoginScreen);
