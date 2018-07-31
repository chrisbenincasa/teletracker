import R from 'ramda';
import React from 'react';
import { FlatList, ListRenderItemInfo, Text, View } from 'react-native';
import { Icon, ListItem } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import { connect, Dispatch } from 'react-redux';

import Header from '../Components/Header/Header';
import { Thing } from '../Model/external/themoviedb';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import NavActions from '../Redux/NavRedux';
import UserActions, { UserState } from '../Redux/UserRedux';
import { Colors } from '../Themes/';
import styles from './Styles/ItemListStyle';
import { State } from '../Redux/State';
import { tracker } from '../Components/Analytics';
import { List } from '../Model';

interface Props {
    componentId: string
    user: UserState
    list: List
    loadUserSelf: (componentId: string) => any
    pushState: (componentId: string, view: object) => any
}

class ItemList extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);
        this.openSearch = this.openSearch.bind(this);
    }

    static get options() {
        return {
            sideMenu: {
                left: {
                    enabled: false
                }
            },
            statusBar: {
                style: 'light'
            }
        }
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
        let view = R.mergeDeepRight(NavigationConfig.DetailView, {
            component: {
                passProps: { itemType: item.type, itemId: item.id },
                options: {
                    sideMenu: {
                        left: {
                            enabled: false
                        }
                    }
                    // statusBar: {
                    //     style: 'dark'
                    // }
                }
            }
        });

        this.props.pushState(this.props.componentId, view);
    }

    renderItem({ item }: ListRenderItemInfo<Thing>) {
        return (
            <ListItem 
                key={item.id}
                title={item.name}
                containerStyle={{ borderBottomWidth: 1, borderBottomColor: 'lightgray' }}
                chevron={true}
                leftIcon={{ name:
                    item.type === 'movie' ? 'movie' 
                        : item.type === 'show' ? 'tv' 
                        : item.type === 'person' ? 'person' 
                        : null
                    }}
                onPress={() => this.goToItemDetail(item)} 
            />
        )
    }

    renderEmpty = () => <Text style={styles.label}> Empty list, yo! </Text>;
    
    keyExtractor: (item: any, index: any) => string = (item, _) => item.id.toString();
    
    oneScreensWorth = 20;
    
    render () {
        return (
            <View style={styles.container}>
                {/* <Header 
                    componentId={this.props.componentId} 
                    centerComponent={{ title: 'My List' }}
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
                    data={this.props.list.things}
                    renderItem={this.renderItem.bind(this)}
                    keyExtractor={this.keyExtractor}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
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
