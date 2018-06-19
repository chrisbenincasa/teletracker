import React, { Component } from 'react';
import { ScrollView, Text, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import { Button, Icon } from 'react-native-elements';
// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
import styles from './Styles/MenuScreenStyle';

class MenuScreen extends Component {
  render () {
    return (
      <ScrollView style={styles.container}>
        <KeyboardAvoidingView behavior='position'>
          <Button
            icon={{
              name: 'settings',
              buttonStyle: styles.customButtons
            }}
            title='Settings' />
          <Button
            icon={{
              name: 'report',
              buttonStyle: styles.customButtons
            }}
            title='Report a Bug' />
          <Button
            icon={{
              name: 'unarchive',
              buttonStyle: styles.customButtons
            }}
            title='Logout' />
        </KeyboardAvoidingView>
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

export default connect(mapStateToProps, mapDispatchToProps)(MenuScreen);
