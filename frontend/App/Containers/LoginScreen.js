import React, { Component } from 'react';
import {
  ScrollView,
  Text,
  TextInput,
  View
} from 'react-native';
import { Card, Button, FormLabel, FormInput } from "react-native-elements";
import { connect } from 'react-redux';
import Header from '../Components/Header';


// Styles
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
        <Header />
        <Card>
          <FormLabel>Email</FormLabel>
          <FormInput placeholder="Email address..." />
          <FormLabel>Password</FormLabel>
          <FormInput secureTextEntry placeholder="Password..." />
          
          <Button
            buttonStyle={{ marginTop: 20 }}
            backgroundColor="#03A9F4"
            title="Login"
            onPress={() => this.props.navigation.navigate('ItemList')} 
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

const mapDispatchToProps = (dispatch) => {
  return {
  }
};

export default connect(mapStateToProps, mapDispatchToProps, null, { withRef: true })(LoginScreen);
