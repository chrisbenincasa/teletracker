import React, { Component } from 'react';
import { KeyboardAvoidingView, ScrollView, Text, View } from 'react-native';
import { Avatar, List, ListItem } from 'react-native-elements';
import { connect } from 'react-redux';

import Logo from '../Components/Logo';
import State from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import styles from './Styles/MenuScreenStyle';

// Styles
const menuItems = [
    {
        title: 'Settings',
        icon: 'settings'
    },
    {
        title: 'Report a Bug',
        icon: 'report'
    },
    {
        title: 'Logout',
        icon: 'unarchive'
    }
];

interface Props {
    user: UserState,
    logout: () => any
}

class MenuScreen extends Component<Props> {
    render () {
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
                    <Text>{this.props.user.details.name}</Text>
                </View>
                <KeyboardAvoidingView behavior='position'>
                    <List>
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
                            onPress={() => this.props.logout()}
                        />
                    </List>
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

const mapDispatchToProps = (dispatch) => {
  return {
      logout: () => dispatch(UserActions.logoutRequest())
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(MenuScreen);
