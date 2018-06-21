import React, { Component } from 'react';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { Colors } from '../../Themes/';

import * as NavigationConfig from '../../Navigation/NavigationConfig';

export default class HeaderLeft extends Component {
    constructor(props) {
        super(props);
        this.openMenu = this.openMenu.bind(this);
    }

    openMenu() {
        Navigation.push(this.props.componentId, NavigationConfig.MenuView);
    }

    render() {
        return (
            <Icon 
                name="menu"
                color="#fff"
                underlayColor={Colors.headerBackground}
                onPress={this.openMenu} />
        );
    }
}