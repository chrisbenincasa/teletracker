import R from 'ramda';
import React from 'react';
import { Image, FlatList, ListRenderItemInfo, Text, View } from 'react-native';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { NavigationScreenProp } from 'react-navigation';
import { connect, Dispatch } from 'react-redux';
import { Card, ListItem } from 'react-native-paper';
import getMetadata from '../Components/Helpers/getMetadata';

import { appVersion, tracker } from '../Components/Analytics';
import { List } from '../Model';
import { Thing } from '../Model/external/themoviedb';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import NavActions from '../Redux/NavRedux';
import { State } from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import styles from './Styles/ItemListStyle';

interface Props {
    componentId: string
    user: UserState
    list: List
    loadUserSelf: (componentId: string) => any
    pushState: (componentId: string, view: object) => any
    navigation: NavigationScreenProp<any>
}

class ItemList extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);
        this.renderItem = this.renderItem.bind(this);
        this.openSearch = this.openSearch.bind(this);
    }

    static navigationOptions = ({ navigation }: { navigation: NavigationScreenProp<any> }) => {
        return {
            title: navigation.getParam('list').name
        };
    }

    state = {};

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    componentDidMount() {
        tracker.trackScreenView('Item List');
    }

    getListSections() {
        if (this.props.user.details && !this.props.user.fetching) {
            const { lists } = this.props.user.details;
            return lists.map(list => {
                return {
                    key: list.name,
                    data: list.things
                };
            })
        } else {
            return [];
        }
    }
    
    openSearch() {
        Navigation.mergeOptions(this.props.componentId, {
            bottomTabs: {
                currentTabIndex: 1
            }
        });
    }

    goToItemDetail(item: Thing) {
        // Track when users navigate to an item from search screen
        tracker.trackEvent('search-action', 'view-item-details', {
            label: appVersion
        });

        this.props.navigation.navigate('DetailScreen', { itemType: item.type, itemId: item.id });
    }

    renderItem({ item }: ListRenderItemInfo<Thing>) {
        return (
            <ListItem 
                key={item.id}
                title={item.name}
                numberOfLines={2}
                description={item.type}
                avatar={
                    <Image
                        source={{uri: 'https://image.tmdb.org/t/p/w92' + '/bvYjhsbxOBwpm8xLE5BhdA3a8CZ.jpg'}}
                        // + getMetadata.getPosterPath(item)}}
                        style={{
                            width: 40,
                            height: 60
                        }}
                    />
                }
                containerStyle={{
                    borderBottomWidth: 1,
                    borderBottomColor: 'lightgray'
                }}
                onPress={() => this.goToItemDetail(item)} 
            />
        )
    }

    renderEmpty = () => {
        return (
            <Card style={{
                flex: 1,
                flexDirection: 'row',
                margin: 8
            }}>
                <View style={styles.defaultScreen}>
                    <Icon
                        name='mood-bad'
                        color='#476DC5'
                        size={75}
                        containerStyle={{
                            height: 75,
                            marginBottom: 20
                        }}
                    />
                    <Text> Empty list, yo!  </Text>
                </View>
            </Card>
        )
    }

    keyExtractor: (item: any, index: any) => string = (item, _) => item.id.toString();
    
    oneScreensWorth = 20;
    
    render () {
        return (
            <View style={styles.container}>
                <View style={{height: 40, padding: 8, alignContent: 'center', flexDirection: 'row'}}>
                    <Text style={{ fontSize: 12 }}>Hey</Text>
                    <Icon iconStyle={{fontSize: 12 }} name='sort' />
                </View>
                <FlatList
                    contentContainerStyle={styles.listContent}
                    data={this.props.list.things}
                    renderItem={this.renderItem}
                    keyExtractor={this.keyExtractor}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                    style={{flex: 0}}
                />
            </View>
        )
    }
};

const mapStateToProps = (state: State) => {
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

export default connect(mapStateToProps, mapDispatchToProps)(ItemList);
