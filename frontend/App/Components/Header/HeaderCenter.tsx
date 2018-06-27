import React, { Component } from 'react';
import { Text, View } from 'react-native';

export default class HeaderCenter extends Component {
  render() {
    return (
      <Text style={{color:'#fff'}}>{ this.props.centerComponent && this.props.centerComponent.title ? this.props.centerComponent.title : null }</Text>
    )
  }
};