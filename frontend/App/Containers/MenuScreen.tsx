import React, { Component } from 'react';
import { KeyboardAvoidingView, ScrollView, Text, View } from 'react-native';
import { Avatar, ListItem } from 'react-native-elements';
import { connect, Dispatch } from 'react-redux';

import { tracker } from '../Components/Analytics';
import Logo from '../Components/Logo';
import State from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import styles from './Styles/MenuScreenStyle';

// Styles
interface Props {
    componentId: string,
    user: UserState,
    logout: (componentId: string) => any
}

class MenuScreen extends Component<Props> {

    componentDidMount() {
        tracker.trackScreenView('Menu');
    }

    render() {
        return (
            <ScrollView style={styles.container}>
                <Logo />
                <View style={{ padding: 10 }}>
                    <Avatar
                        medium={true}
                        rounded
                        source={{ uri: "https://s3.amazonaws.com/uifaces/faces/twitter/ladylexy/128.jpg" }}
                        onPress={() => console.log("Works!")}
                        activeOpacity={0.7}
                    />
                    <Text>{this.props.user && this.props.user.details && this.props.user.details.name ? this.props.user.details.name : null}</Text>
                </View>
                <KeyboardAvoidingView behavior='position'>
                    <ListItem
                        title="Settings"
                        leftIcon={{ name: "settings" }}
                    />
                    <ListItem
                        title="Report A Bug"
                        leftIcon={{ name: "report" }}
                    />
                    <ListItem
                        title="Logout"
                        leftIcon={{ name: "unarchive" }}
                        onPress={() => this.props.logout(this.props.componentId)}
                    />
                </KeyboardAvoidingView>
            </ScrollView>
        )
    }
}

const mapStateToProps = (state: State) => {
    return {
        user: state.user
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        logout: (componentId: string) => dispatch(UserActions.logoutRequest(componentId))
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(MenuScreen);
