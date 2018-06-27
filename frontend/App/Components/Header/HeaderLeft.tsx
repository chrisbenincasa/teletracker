import React, { Component } from 'react';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { Colors } from '../../Themes/';

export default class HeaderLeft extends Component {
    constructor(props) {
        super(props);
        this.showLeftMenu = this.showLeftMenu.bind(this);
        this.goBack = this.goBack.bind(this);
    }

    async showLeftMenu() {
        await Navigation.mergeOptions(this.props.componentId, {
            sideMenu: {
                left: {
                    visible: true
                }
            }
        });
    }

    async goBack() {
        await Navigation.pop(this.props.componentId);
    }

    render() {
        return (
            <Icon 
                name={ this.props.leftComponent && this.props.leftComponent.icon ? this.props.leftComponent.icon : 'menu' }
                color="#fff"
                underlayColor={Colors.transparent}
                
                onPress={this.props.leftComponent && this.props.leftComponent.back ? this.goBack : this.showLeftMenu}
            />
        );
    }
}