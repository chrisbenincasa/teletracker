import { ActivityIndicator, View } from "react-native";
import React from 'react';
import styles from './Styles/LoginScreenStyle';

type Props = {}

export default class LoadingScreen extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
    }

    render() {
        return (
            <View style={styles.container}>
                <ActivityIndicator />
            </View>
        )
    }
}