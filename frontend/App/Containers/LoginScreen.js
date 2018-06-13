import React, { Component } from 'react';
import {
  ScrollView,
  Text,
  TextInput,
  View,
  Button
} from 'react-native';
import DevscreensButton from '../../ignite/DevScreens/DevscreensButton.js';

import { connect } from 'react-redux';
// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
import styles from './Styles/LoginScreenStyle';

class LoginScreen extends Component {
  render () {
    return (
      <ScrollView style={{padding: 20}}>
          <Text 
              style={{fontSize: 27}}>
              Login
          </Text>
          <TextInput placeholder='Username' />
          <TextInput placeholder='Password' />
          <View style={{margin:7}} />
          <Button 
                  onPress={this.props.onLoginPress}
                  title="Submit"
              />
              <DevscreensButton />
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
