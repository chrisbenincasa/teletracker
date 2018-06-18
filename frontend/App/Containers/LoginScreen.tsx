import React, { Component } from 'react';
import {
  ScrollView,
  Text,
  TextInput,
  View
} from 'react-native';
import { Card, Button, FormLabel, FormInput } from "react-native-elements";
import { connect, Dispatch } from 'react-redux';
import Logo from '../Components/Logo';
import UserActions, { SignupState } from '../Redux/UserRedux'
import styles from './Styles/LoginScreenStyle';
import { Navigation } from 'react-native-navigation';

class LoginScreen extends Component {
  static get options() {
    return {
      _statusBar: {
        backgroundColor: 'transparent',
        style: 'dark',
        drawBehind: true
      }
    }
  }

  goToSignup() {
    Navigation.push(this.props.componentId, {
      component: {
        name: 'navigation.main.SignupScreen',
        options: {
          topBar: {
            visible: false
          }
        }
      }
    })
  }

  render () {
    return (
      <ScrollView style={styles.container}>
        <Logo />
        <Card>
          <FormLabel>Email</FormLabel>
          <FormInput 
            placeholder="Email address..."
            autoCapitalize='none'
            onChangeText={(email) => this.setState({ email })} />
          <FormLabel>Password</FormLabel>
          <FormInput 
            secureTextEntry 
            placeholder="Password..."
            autoCapitalize='none'
            onChangeText={(password) => this.setState({ password }) } />
          
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

const mapStateToProps = (state) => {
  return {
  }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    login: (componentId, email, password) => {
      dispatch(UserActions.loginRequest(componentId, email, password));
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps, null, { withRef: true })(LoginScreen);
