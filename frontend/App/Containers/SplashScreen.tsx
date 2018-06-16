import * as React from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView, ActivityIndicator } from 'react-native';
import Header from '../Components/Header';
import { ApplicationStyles } from '../Themes';

class SplashScreen extends React.PureComponent {
    render() {
        return (
            <View style={ApplicationStyles.screen.container}>
                <Header />
                <ActivityIndicator animating />
            </View>
        )
    }
}

export default SplashScreen;