import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';

type Props = {
    color?: string
    iconName?: string,
    iconType?: string,
    text?: string,
    component?: React.Component,
    onPress?: (self: ModalHeaderButton) => any,
    getModalRef: () => string
}

export default class ModalHeaderButton extends React.PureComponent<Props> {
    handlePress() {
        Navigation.dismissModal(this.props.getModalRef());
    }

    render() {
        if (this.props.component) {
            const C = this.props.component;
            return <C {...this.props} />
        } else {
            return (
                <View>
                    <TouchableOpacity>
                        <Icon 
                            name={this.props.iconName || 'ios-arrow-back'} 
                            type={this.props.iconType || 'ionicon'}
                            color={this.props.color || 'black'} 
                            onPress={this.props.onPress ? () => this.props.onPress(this) : () => this.handlePress()} 
                        />
                    </TouchableOpacity>
                    {this.props.text ? 
                        <Text onPress={this.props.onPress ? () => this.props.onPress(this) : () => this.handlePress()}>{this.props.text}</Text> 
                        : null
                    }
                </View>
            );
        }
    }
}