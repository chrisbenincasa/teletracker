import React, { Component } from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import { Card, Button, FormLabel, FormInput } from "react-native-elements";
import Header from '../Components/Header';

// Styles
import styles from './Styles/SignupScreenStyle';

class SignupScreen extends Component {
  render () {
    return (
      <ScrollView style={styles.container}>
        <Header />
        <Card>
          <FormLabel>Email</FormLabel>
          <FormInput placeholder="Email address..." />
          <FormLabel>Password</FormLabel>
          <FormInput secureTextEntry placeholder="Password..." />
          <FormLabel>Confirm Password</FormLabel>
          <FormInput secureTextEntry placeholder="Confirm Password..." />

          <Button
            buttonStyle={{ marginTop: 20 }}
            backgroundColor="#03A9F4"
            title="Sign Up"
            onPress={() => this.props.navigation.navigate('ItemList')} 
          />
          <Button
            buttonStyle={{ marginTop: 20 }}
            backgroundColor="transparent"
            textStyle={{ color: "#bcbec1" }}
            title="Login"
            onPress={() => this.props.navigation.navigate('LoginScreen')} 
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

export default connect(mapStateToProps, mapDispatchToProps)(SignupScreen);
