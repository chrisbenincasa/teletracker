import React, { Component } from 'react';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { Colors } from '../../Themes/';

export default class HeaderLeft extends Component {
    constructor(props) {
        super(props);
        this.showLeftMenu = this.showLeftMenu.bind(this);
    }

    showLeftMenu() {
        Navigation.mergeOptions(this.props.componentId, {
            sideMenu: {
                left: {
                    visible: true
                }
            }
        });
    }

 render() {
        return (
            <Icon 
                name="menu"
                color="#fff"
                underlayColor={Colors.headerBackground}
                onPress={this.showLeftMenu} 
            />
        );
    }
}