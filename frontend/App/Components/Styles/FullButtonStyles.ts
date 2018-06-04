import { StyleSheet, ViewStyle, TextStyle } from 'react-native'
import { Fonts, Colors } from '../../Themes/'

export interface FullButtonStyle {
  button: ViewStyle,
  buttonText: TextStyle
}

export const FullButtonStyleSheet = StyleSheet.create<FullButtonStyle>({
  button: {
    marginVertical: 5,
    borderTopColor: Colors.fire,
    borderBottomColor: Colors.bloodOrange,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    backgroundColor: Colors.ember
  },
  buttonText: {
    margin: 18,
    textAlign: 'center',
    color: Colors.snow,
    fontSize: Fonts.size.medium,
    fontFamily: Fonts.type.bold
  }
});