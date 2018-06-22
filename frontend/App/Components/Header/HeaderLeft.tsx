import React, { Component } from 'react';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { Colors } from '../../Themes/';
const { BackHandler } = require('react-native');

export default class HeaderLeft extends Component {
    constructor(props) {
        super(props);
        this.showLeftMenu = this.showLeftMenu.bind(this);
        this.goBack = this.goBack.bind(this);
    }

    async showLeftMenu() {
        console.tron.log("FIRE!");
        await Navigation.mergeOptions(this.props.componentId, {
            sideMenu: {
                left: {
                    visible: true
                }
            }
        });
    }

    async goBack() {
        await Navigation.pop(this.props.componentId, {
            animations: {
                y: {
                    from: 1000,
                    to: 0,
                    duration: 500,
                    interpolation: 'accelerate',
                  },
                  alpha: {
                    from: 0,
                    to: 1,
                    duration: 400,
                    startDelay: 100,
                    interpolation: 'accelerate'
                  }
            } 
        });
    }

    render() {
        return (
            <Icon 
                name={ this.props.leftComponent && this.props.leftComponent.icon ? this.props.leftComponent.icon : 'menu' }
                color="#fff"
                underlayColor={Colors.headerBackground}
                
                onPress={this.props.leftComponent && this.props.leftComponent.back ? this.goBack : this.showLeftMenu}

                // onPress={this.showLeftMenu} 
            />
        );
    }
}