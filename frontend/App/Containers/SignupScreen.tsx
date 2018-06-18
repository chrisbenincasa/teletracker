import React, { Component } from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView, ActivityIndicator } from 'react-native';
import { connect, Dispatch, MapDispatchToProps } from 'react-redux';
import FullButton from '../Components/FullButton';
import UserActions, { SignupState } from '../Redux/UserRedux'
import { State as AppState } from '../Redux/State';
import { Card, Button, FormLabel, FormInput } from "react-native-elements";
import Logo from '../Components/Logo';

// Styles
import styles from './Styles/SignupScreenStyle';
import { Navigation } from 'react-native-navigation';

interface State {
  username?: string,
  password?: string,
  email?: string
}

interface Props {
  signup: SignupState,
  navigation: any,
  onSignUpAttempt: any,
  onLoginPress: any
}

class SignupScreen extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { username: 'Christian', password: 'password', email: 'test@test.com' };
  }

  goToLogin() {
    Navigation.pop(this.props.componentId, {});
  }

  render() {
    return (
      <ScrollView style={styles.container}>
        <Logo />
        <Card>
          <FormLabel>Username</FormLabel>
          <FormInput 
            placeholder="Username..." 
            onChangeText={(username) => this.setState({ username })} 
            value={this.state.username}
            editable={!this.props.signup.fetching}
            autoCapitalize='none' />
          <FormLabel>Email</FormLabel>
          <FormInput 
            placeholder="Email address..."
            onChangeText={(email) => this.setState({ email })}
            value={this.state.email}
            editable={!this.props.signup.fetching}
            autoCapitalize='none' />
          <FormLabel>Password</FormLabel>
          <FormInput 
            secureTextEntry 
            placeholder="Password..." 
            editable={!this.props.signup.fetching}
            autoCapitalize='none'
            onChangeText={(password) => this.setState({ password })}
            value={this.state.password} />
          <FormLabel>Confirm Password</FormLabel>
          <FormInput 
            secureTextEntry 
            placeholder="Confirm Password..."
            editable={!this.props.signup.fetching}
            autoCapitalize='none'
            onChangeText={(password) => this.setState({ password })}
            value={this.state.password} />

          <Button
            buttonStyle={{ marginTop: 20 }}
            backgroundColor="#03A9F4"
            title="Sign Up"
            onPress={() => this.props.onSignUpAttempt(this.props.componentId, this.state)} 
          />
          <Button
            buttonStyle={{ marginTop: 20 }}
            backgroundColor="transparent"
            textStyle={{ color: "#bcbec1" }}
            title="Login"
            onPress={() => this.goToLogin()}
          />
          <ActivityIndicator animating={this.props.signup.fetching} />
        </Card>
        <Text style={{textAlign: 'center', color: 'green' }}>{this.props.signup.success ? 'Done!' : ''}</Text>
        <Text style={{ textAlign: 'center', color: 'red' }}>{this.props.signup.error ? 'Something went wrong!' : ''}</Text>
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
    onSignUpAttempt: (componentId: string, {username, email, password}: State) => {
      dispatch(UserActions.userSignupRequest(componentId, username, email, password ));
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(SignupScreen);
