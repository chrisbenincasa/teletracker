import React from 'react';
import { FlatList, ListRenderItemInfo, Text, View } from 'react-native';
import { connect, Dispatch } from 'react-redux';

import { List } from '../Model';
import NavActions from '../Redux/NavRedux';
import { State as ReduxState } from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import { Colors } from '../Themes/';
import styles from './Styles/ItemListStyle';
import Header from '../Components/Header/Header';
import { Icon, ListItem } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import CreateNewListModal from '../Containers/CreateNewListModal';
import { EventEmitter } from 'events';

type Props = {
    componentId: string
    user: UserState
    loadUserSelf: (componentId: string) => any
    pushState: (componentId: string, view: object) => any
}

type State = {

}

class ListDetailScreen extends React.PureComponent<Props, State> {
    state = {};

    goToList(list: List) {
        this.props.pushState(this.props.componentId, {
            component: {
                name: 'navigation.main.ItemList',
                passProps: {
                    list
                },
                options: {
                    topBar: {
                        visible: true,
                        title: {
                            text: list.name
                        }
                    },
                    animated: true,
                    sideMenu: {
                        left: {
                            enabled: true
                        }
                    }
                }
            }
        })
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
                chevron={true} 
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
                {/* <Header
                    componentId={this.props.componentId}
                    centerComponent={{ title: 'My Lists' }}
                    rightComponent={
                        <Icon
                            name='search'
                            color='#fff'
                            underlayColor={Colors.headerBackground}
                            onPress={this.openSearch}
                        />
                    }
                /> */}
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

export const ListDetailNavOptions = {
    bottomTab: {
        text: 'My List',
        icon: require('../Images/Icons/list-icon.png'),
        testID: 'FIRST_TAB_BAR_BUTTON'
    },
    topBar: {
        title: {
            text: 'My Lists',
            color: 'white'
        },
        background: {
            color: Colors.headerBackground
        },
        rightButtons: [{
            id: 'CreateListButton',
            component: {
                name: 'navigation.topBar.Button',
                passProps: {
                    iconName: 'add-to-list',
                    iconType: 'entypo',
                    color: 'white',
                    onPress: () => Navigation.showModal({
                        stack: {
                            children: [{
                                component: {
                                    name: 'navigation.main.CreateNewListModal',
                                    options: CreateNewListModal.options
                                }
                            }]
                        }
                    })
                }
            }
        }],
        visible: true
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(ListDetailScreen);
