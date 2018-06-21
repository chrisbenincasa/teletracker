import React, { Component } from 'react';
import { ScrollView, Text, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import Logo from '../Components/Logo';
import { Button, Icon, List, ListItem } from 'react-native-elements';

// Styles
import styles from './Styles/MenuScreenStyle';

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

class MenuScreen extends Component {
    render () {
        return (
            <ScrollView style={styles.container}>
                <Logo />
                <KeyboardAvoidingView behavior='position'>
                    <List>
                    {
                        menuItems.map((item, i) => (
                            <ListItem
                                key={i}
                                title={item.title}
                                leftIcon={{name: item.icon}}
                            />
                        ))
                    }
                    </List>
                </KeyboardAvoidingView>
            </ScrollView>
        )
    }
}

const mapStateToProps = (state) => {
  return {
  }
};

const mapDispatchToProps = (dispatch) => {
  return {
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(MenuScreen);
