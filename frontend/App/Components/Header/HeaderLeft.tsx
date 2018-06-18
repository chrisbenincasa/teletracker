import React, { Component } from 'react';
import { Text, View } from 'react-native';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import * as NavigationConfig from '../../Navigation/NavigationConfig';

export default class HeaderLeft extends Component {
constructor(props) {
  super(props);
  this.openMenu = this.openMenu.bind(this);
}
  openMenu() {
    console.log(NavigationConfig.MenuScreen);
    Navigation.push(this.props.componentId, NavigationConfig.MenuScreen);
  };
 
  render () {
    
    return (
      <Icon 
        name='menu' 
        color='#fff' 
        onPress={this.openMenu.bind(this)}
      />
    )
  }
};