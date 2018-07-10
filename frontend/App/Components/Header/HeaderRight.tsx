import React, { Component } from 'react';
import { View } from 'react-native';
import { Icon } from 'react-native-elements';
import { Colors } from '../../Themes/';

import * as NavigationConfig from '../../Navigation/NavigationConfig';
import { reverse } from 'dns';

export default class HeaderRight extends Component {
    constructor(props) {
        super(props);
    }

    render () {
        return (
            <View>
                {
                    this.props.rightComponent ?
                    this.props.rightComponent
                    : null
                }
                </View>
        )
    }
};