import React, { Component } from 'react';
import { KeyboardAvoidingView, ScrollView, Text, View } from 'react-native';
import { Avatar } from 'react-native-elements';
import { connect, Dispatch } from 'react-redux';
import { DrawerSection, DrawerItem } from 'react-native-paper'

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
                    <DrawerSection>
                        <DrawerItem
                            label='Settings'
                            icon='settings'
                            active={false}
                            onPress={() => this.props.settings(this.props.componentId)}
                        />
                        <DrawerItem
                            label='Logout'
                            icon='unarchive'
                            active={false}
                            onPress={() => this.props.logout(this.props.componentId)}
                        />
                    </DrawerSection>
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
