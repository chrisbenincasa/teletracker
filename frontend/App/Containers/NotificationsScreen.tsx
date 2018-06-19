import React, { Component } from 'react';
import { View, Text, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import { Card, ListItem, Icon, Header } from 'react-native-elements';
import HeaderLeft from '../Components/Header/HeaderLeft';
import HeaderCenter from '../Components/Header/HeaderCenter';
import HeaderRight from '../Components/Header/HeaderRight';
// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
import styles from './Styles/NotificationsScreenStyle';

class NotificationsScreen extends Component {
  render () {
    return (
      <View style={styles.container}>
      <Header>
        <HeaderLeft {...this.props} />
        <HeaderCenter />
        <HeaderRight />
      </Header> 
      <Text>Notifications Screen</Text>
      </View>
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

export default connect(mapStateToProps, mapDispatchToProps)(NotificationsScreen);
