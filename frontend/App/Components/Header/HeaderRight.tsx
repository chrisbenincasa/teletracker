import React, { Component } from 'react';
import { View } from 'react-native';

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