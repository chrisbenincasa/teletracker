import React, { Component } from 'react';
import { Text, View } from 'react-native';
import Search from 'react-native-search-box';

interface Props {
  centerComponent?: { title: string }
}

export default class HeaderCenter extends Component<Props> {
  render() {
    return (
      <View>
        <Text style={{color:'#fff'}}>{ this.props.centerComponent && this.props.centerComponent.title ? this.props.centerComponent.title : null }</Text>
      </View>
    )
  }
};