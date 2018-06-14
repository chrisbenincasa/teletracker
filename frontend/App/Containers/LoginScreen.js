import React, { Component } from 'react';
import {
  ScrollView,
  Text,
  TextInput,
  View,
  Button
} from 'react-native';
import FullButton from '../Components/FullButton';
import { connect } from 'react-redux';

// Styles
import styles from './Styles/LoginScreenStyle';

class LoginScreen extends Component {
  render () {
    return (
      <ScrollView style={{padding: 20}}>
        <Text 
            style={{fontSize: 27, textAlign: 'center'}}>
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
            style={{backgroundColor: 'red'}} 
            onPress={() => this.props.navigation.navigate('SignupScreen')} 
          />
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
