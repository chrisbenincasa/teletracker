import React, { Component } from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import FullButton from '../Components/FullButton';

// Styles
import styles from './Styles/SignupScreenStyle';

class SignupScreen extends Component {
  render () {
    return (
      <ScrollView style={styles.container}>
        <Text 
            style={{fontSize: 27, textAlign: 'center'}}>
            Sign Up
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
        <TextInput placeholder='Email' style={{
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
            text='Sign Up' 
            style={{backgroundColor: 'red'}} 
            onPress={() => this.props.navigation.navigate('ItemList')} 
        />
        <FullButton 
            onPress={this.props.onLoginPress}
            text="Login"
            onPress={() => this.props.navigation.navigate('LoginScreen')} 
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

export default connect(mapStateToProps, mapDispatchToProps)(SignupScreen);
