import React, { Component } from 'react';
import {
  ScrollView,
  Text,
  TextInput,
  View
} from 'react-native';
import { Card, Button, FormLabel, FormInput } from "react-native-elements";
import { connect } from 'react-redux';

// Styles
import styles from './Styles/LoginScreenStyle';

class LoginScreen extends Component {
  render () {
    return (
      <ScrollView style={{padding: 20}}>
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
            title="Sign Up"
            onPress={() => this.props.navigation.navigate('SignupScreen')} 
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

export default connect(mapStateToProps, mapDispatchToProps)(LoginScreen);
