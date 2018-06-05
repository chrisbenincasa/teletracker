import React, { Component } from 'react';
import { Text, TouchableOpacity, ViewStyle } from 'react-native';

import ExamplesRegistry from '../Services/ExamplesRegistry';
import { FullButtonStyleSheet } from './Styles/FullButtonStyles';

// Note that this file (App/Components/FullButton) needs to be
// imported in your app somewhere, otherwise your component won't be
// compiled and added to the examples dev screen.

// Ignore in coverage report
/* istanbul ignore next */
ExamplesRegistry.addComponentExample('Full Button', () =>
  <FullButton
    text='Hey there'
    onPress={() => window.alert('Full Button Pressed!')}
  />
)

export default class FullButton extends Component<FullButtonProps> {
  render () {
    return (
      <TouchableOpacity style={[FullButtonStyleSheet.button, this.props.styles]} onPress={this.props.onPress}>
        <Text style={FullButtonStyleSheet.buttonText}>{this.props.text && this.props.text.toUpperCase()}</Text>
      </TouchableOpacity>
    )
  }
}

interface FullButtonProps {
  text: string;
  onPress: () => void;
  styles?: ViewStyle;
}