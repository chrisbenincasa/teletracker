import React, { Component } from 'react';
import { Text, View } from 'react-native';
import { Icon } from 'react-native-elements';

export default class Header extends Component {
  render () {
    return (
      <View>
        <Icon name='tv'/>
        <Text h1 style={{textAlign: 'center', fontWeight: 'bold', fontSize: 20}}>Teletracker</Text>
      </View>
    )
  }
};