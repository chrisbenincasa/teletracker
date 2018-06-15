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

class LoginScreen extends Component {
  render () {
    return (
<<<<<<< HEAD
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
            title="Sign Up"
=======
      <ScrollView style={{padding: 20}}>
        <Text 
            style={{fontSize: 27, textAlign: 'center', marginTop: 20}}>
            Login
        </Text>
        <TextInput placeholder='Username' style={{
            fontSize: 27, 
            flex: 1,
            borderColor: '#000', 
            borderStyle: 'solid', 
            borderWidth: 1, 
            padding: 10, 
            margin: 5
        }}/>
        <TextInput placeholder='Password' style={{
            fontSize: 27, 
            flex: 1,
            borderColor: '#000', 
            borderStyle: 'solid', 
            borderWidth: 1, 
            padding: 10, 
            margin: 5
        }}/>
        <View style={{margin:7}} />
        <FullButton 
            onPress={this.props.onLoginPress}
            text="Login"
            onPress={() => this.props.navigation.navigate('ItemList')} 
        />
        <FullButton 
            text='Sign Up' 
            styles={{backgroundColor: 'red'}} 
>>>>>>> 52d1920... Stop point 1
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
