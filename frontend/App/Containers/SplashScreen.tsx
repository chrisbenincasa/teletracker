import * as React from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView, ActivityIndicator } from 'react-native';
import Logo from '../Components/Logo';
import { ApplicationStyles } from '../Themes';

class SplashScreen extends React.PureComponent {
    render() {
        return (
            <View style={ApplicationStyles.screen.container}>
                <Logo />
                <ActivityIndicator animating />
            </View>
        )
    }
}

export default SplashScreen;