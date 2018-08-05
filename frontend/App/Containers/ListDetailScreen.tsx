import React from 'react';
import { FlatList, ListRenderItemInfo, StatusBar, Text, TouchableOpacity, View } from 'react-native';
import { Icon, ListItem } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { NavigationScreenProp } from 'react-navigation';
import { connect, Dispatch } from 'react-redux';

import { List } from '../Model';
import NavActions from '../Redux/NavRedux';
import { State as ReduxState } from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import { Colors } from '../Themes/';
import styles from './Styles/ItemListStyle';

type Props = {
    componentId: string
    user: UserState
    loadUserSelf: (componentId: string) => any
    pushState: (componentId: string, view: object) => any,
    navigation: NavigationScreenProp<any>
}

type State = {

}

class ListDetailScreen extends React.PureComponent<Props, State> {
    state = {};

    static drawerOptions = {
        enabled: true
    }

    static navigationOptions = ({ navigation }: { navigation: NavigationScreenProp<any> }) => {
        return {
            drawer: {
                enabled: true
            },
            title: 'My Lists',
            headerRight: (
                <TouchableOpacity style={{ marginHorizontal: 10 }}>
                    <Icon
                        name='add-to-list'
                        type='entypo'
                        color='white'
                        underlayColor={Colors.headerBackground}
                        onPress={() => navigation.navigate('CreateListModal')}
                    />
                </TouchableOpacity>
            )
        };
    }

    goToList(list: List) {
        this.props.navigation.push('SpecificList', { list });
    }

    keyExtractor: (item: List) => string = (item) => item.id.toString();

    renderItem({ item }: ListRenderItemInfo<List>) {
        let things = item.things || [];
        let subtitle = `${things.length} Item` + (things.length == 1 ? '' : 's');
        return (
            <ListItem 
                key={item.id} 
                title={item.name} 
                subtitle={subtitle} 
                onPress={() => this.goToList(item)} 
                containerStyle={{borderBottomWidth: 1, borderBottomColor: 'lightgray'}}
            />
        );
    }

    openSearch() {
        Navigation.mergeOptions(this.props.componentId, {
            bottomTabs: {
                currentTabIndex: 1
            }
        });
    }

    renderEmpty = () => <Text style={styles.label}> No lists, yo! </Text>;

    oneScreensWorth = 20;

    render() {
        return (
            <View style={styles.container}>
                <StatusBar barStyle='light-content' />
                <FlatList
                    contentContainerStyle={styles.listContent}
                    data={this.props.user.details.lists}
                    renderItem={this.renderItem.bind(this)}
                    keyExtractor={this.keyExtractor}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                />
            </View>
        );
    }
}

const mapStateToProps = (state: ReduxState) => {
    return {
        user: state.user
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        },
        pushState: (componentId: string, view: any) => {
            dispatch(NavActions.pushState(componentId, view));
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ListDetailScreen);
