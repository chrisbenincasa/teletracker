import React, { Component } from 'react';
import { View } from 'react-native';
import {
    Button,
    Card,
    TextInput
} from 'react-native-paper';
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
        this.goToSignup = this.goToSignup.bind(this);
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
            <View style={styles.container}>
                <Logo />
                <Card style={{
                    margin: 15,
                    marginBottom: 50,
                    flexGrow: 0,
                    flexDirection: 'row'
                }}>
                    <TextInput
                        placeholder='Email address...'
                        label='Email'
                        keyboardType='email-address'
                        returnKeyType='next'
                        onSubmitEditing={() => this.inputs.password.focus()}
                        onChangeText={(email) => this.setState({ email })} 
                        style={{margin: 5}}
                    />
                    <TextInput
                        secureTextEntry
                        placeholder='Password...'
                        label='Password'
                        autoCapitalize='none'
                        ref={input => this.inputs.password = input}
                        onChangeText={(password) => this.setState({ password })}
                        style={{margin: 5}}
                    />
                    <Button
                        primary
                        raised
                        style={{ margin: 5 }}
                        backgroundColor='#03A9F4'
                        onPress={() => this.props.login(this.props.componentId, this.state.email, this.state.password)}
                    >
                        Login
                    </Button>
                    <Button
                        style={{ margin: 5 }}
                        backgroundColor='transparent'
                        textStyle={{ color: '#bcbec1' }}
                        onPress={this.goToSignup}
                    >
                        Sign Up
                    </Button>
                </Card>
            </View>
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
