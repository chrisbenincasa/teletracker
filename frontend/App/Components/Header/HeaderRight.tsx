import React, { Component } from 'react';
import { View } from 'react-native';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { Colors } from '../../Themes/';

import * as NavigationConfig from '../../Navigation/NavigationConfig';
import { reverse } from 'dns';

export default class HeaderRight extends Component {
    constructor(props) {
        super(props);
        this.openSearch = this.openSearch.bind(this);
    }

    openSearch() {
        Navigation.push(this.props.componentId, NavigationConfig.SearchView);
    }

    render () {
        return (
            <View>
                {this.props.centerComponent ?
            <Icon 
                name='search'
                color='#fff'
                underlayColor={Colors.headerBackground}
                onPress={this.openSearch} />
                : null}
            </View>
        )
    }
};