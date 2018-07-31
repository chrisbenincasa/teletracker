import * as R from 'ramda';
import React, { Component } from 'react';
import {
    View,
    FlatList,
    Text,
    Image,
    TouchableHighlight,
    ActivityIndicator,
    Dimensions
} from 'react-native';
import { Icon, ListItem, Button, Divider } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import Search from 'react-native-search-box';
import checkDevice from '../Components/Helpers/checkOrientation';
import getMetadata from '../Components/Helpers/getMetadata';
import Header from '../Components/Header/Header';
import { NavigationConfig } from '../Navigation/NavigationConfig';

import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import ListActions from '../Redux/ListRedux';
import SearchActions from '../Redux/SearchRedux';
import ReduxState from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import { tracker, appVersion } from '../Components/Analytics';

import { Colors } from './../Themes/'; //testing only, cleanup later
import styles from './Styles/SearchScreenStyle';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    addItemToList: (componentId: string, listId: string, itemId: string | number) => any,
    addRecentlyViewed: (item: object) => any;
    clearSearch: () => any;
    componentId: string;
    doSearch: (search: String) => any;
    loadUserSelf: (componentId: string) => any;    
    removeAllRecentlyViewed: () => any;    
    removeRecentlyViewed: (item: object) => any;
    search: any;
}

interface State {
    devicetype: string;
    gridView: boolean;
    orientation: string;
    searchText: string;
}

class SearchScreen extends Component<Props, State> {

    constructor() {
        super();
        this.searchTextChanged = this.searchTextChanged.bind(this);
        this.executeSearch = this.executeSearch.bind(this);
        this.renderItem = this.renderItem.bind(this);

        this.state = {
            orientation: checkDevice.isPortrait() ? 'portrait' : 'landscape',
            devicetype: checkDevice.isTablet() ? 'tablet' : 'phone',
            gridView: true
        };

        // Event Listener for orientation changes
        Dimensions.addEventListener('change', () => {
            this.setState({
                orientation: checkDevice.isPortrait() ? 'portrait' : 'landscape'
            });
        });
    }

    private tvResultsLens = R.lensPath(['search', 'results', 'data']);

    changeView = () => {
        this.setState({ 
            gridView: !this.state.gridView
        });
    }

    listTypeIcon() {
        return (
            <Icon 
                name={this.state.gridView ? 'list' : 'apps'}
                color='#fff'
                underlayColor={Colors.headerBackground}
                onPress={this.changeView}
            />
        )
    }

    getItemContainerWidth(){
        return this.state.gridView ? (checkDevice.isLandscape() ? 155 : 178) : 75;
    }

    getItemContainerHeight(){
        return this.state.gridView ? (checkDevice.isLandscape() ? 216 : 246) : 104;
    }

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    componentDidMount() {
        tracker.trackScreenView('Search');
    }

    executeSearch() {
        // Track when users add items from search screen
        tracker.trackEvent('search-action', 'search', {
            label: appVersion
        });

        this.props.doSearch(this.state.searchText);
    }

    searchTextChanged(text: string) {
        return this.setState({ searchText: text });
    }

    // Important: You must return a Promise
    onCancel = () => {
        return new Promise((resolve, reject) => {
            // Track when users cancel search
            tracker.trackEvent('search-action', 'cancel', {
                label: appVersion
            });

            this.props.clearSearch(this.state.searchText);
            resolve();
        });
    }

    addItem(itemId) {
        // Track when users add items from search screen
        tracker.trackEvent('search-action', 'add-item', {
            label: appVersion
        });

        this.props.addItemToList(this.props.componentId, 'default', itemId);
    }

    renderEmpty = () => { 
        return (
            !this.props.search.results && !this.props.search.fetching ?
                <View style={styles.defaultScreen}>
                    <Icon
                        name='search'
                        color='#476DC5'
                        size={55}
                        containerStyle={{height: 44}}
                    />
                    <Text> Search for Movies, TV Shows, or People! </Text>
                    {
                        this.props.search.recentlyViewed && this.props.search.recentlyViewed.length > 0 ? 
                        <View>
                            <Divider style={{ backgroundColor: 'grey', marginVertical: 15 }} />
                            <Text h3>Recently Viewed</Text>
                            {this.props.search.recentlyViewed.map((i) => (
                                <ListItem
                                    roundAvatar
                                    avatar={{uri: 'https://image.tmdb.org/t/p/w154' + getMetadata.getPosterPath(i) }}
                                    key={i.id}
                                    title={i.name}
                                    onPress={() => this.goToItemDetail(i)}
                                    rightIcon={{name: 'close'}}
                                    onPressRightIcon={() => this.props.removeRecentlyViewed(i)}
                                />
                            ))}
                            <Button
                                icon={{name: 'delete'}}
                                title='Clear All Recent Searches'
                                onPress={() => this.props.removeAllRecentlyViewed()}
                                style={{margin: 10}}
                            />
                        </View>
                        : null
                    }
                </View>
            : null 
        )
    };

    renderLoading = () => { }

    // The default function if no Key is provided is index
    // an identifiable key is important if you plan on
    // item reordering.  Otherwise index is fine
    // g = grid, l = list, h = horizontal, v = vertical
    keyExtractor = (item: any, index: any) => item.id + (this.state.gridView ? 'g' : 'l') + (checkDevice.isLandscape() ? 'h' : 'v');

    // How many items should be kept im memory as we scroll?
    oneScreensWorth = 18;
    // oneScreensWorth = (this.state.gridView ? (checkDevice.isLandscape() ? 12 : 18) : (checkDevice.isLandscape() ? 4 : 8));

    goToItemDetail(item: object) {
        // Track when users navigate to an item from search screen
        tracker.trackEvent('search-action', 'view-item-details', {
            label: appVersion
        });

        const view = R.mergeDeepLeft(NavigationConfig.DetailView, {
            component: {
                passProps: { item }
            }
        });

        this.props.addRecentlyViewed(item);

        Navigation.push(this.props.componentId, view);
    }

    getResults(action:string) {
        const tvResults = R.view(this.tvResultsLens, this.props);
        if (tvResults && tvResults.length > 0) {
            return tvResults;
        } else {
            return [];
        }
    }

    renderItem ( { item }:object ) {
        return (
            <View style={{margin: 5}}>

                <TouchableHighlight 
                    activeOpacity={0.3}
                    onPress={() => this.goToItemDetail(item)
                }>
                    <View>
                    <View style={styles.addToList}>
                        <Icon
                            name='add'
                            color='#fff'
                            underlayColor='#000'
                            size={36}
                            onPress={() => this.addItem(item.id)}
                        />
                    </View>
                        { getMetadata.getPosterPath(item) ?
                            <Image
                                style={{ 
                                        flex: 1,
                                        width: this.getItemContainerWidth(),
                                        height: this.getItemContainerHeight(),
                                        backgroundColor: '#C9C9C9',
                                        alignContent: 'center'}}
                                source={{uri: 'https://image.tmdb.org/t/p/w154' + getMetadata.getPosterPath(item) }}
                            /> : 
                            <View style={{ 
                                        flex: 1,
                                        width: this.getItemContainerWidth(),
                                        height: this.getItemContainerHeight(),
                                        backgroundColor: '#C9C9C9',
                                        alignContent: 'center'}}>
                                <Icon name='image' color='#fff' size={50} containerStyle={{flex: 1}}/>
                            </View>
                        }
                    </View>
                </TouchableHighlight>
                <Text 
                    style={{
                        width: this.getItemContainerWidth(),
                        textAlign: 'center', 
                        fontWeight: 'bold'}}
                    numberOfLines={1}
                    ellipsizeMode='tail'
                    onPress={() => this.goToItemDetail(item)}
                >{ item.name }</Text>
                
                {
                    getMetadata.getSeasonCount(item) || getMetadata.getEpisodeCount(item) ?
                        <Text 
                            style={{
                                width: this.getItemContainerWidth(),
                                textAlign: 'center'}}
                            numberOfLines={1}
                            ellipsizeMode='tail'
                            onPress={() => this.goToItemDetail(item)}
                        >{ `${getMetadata.getSeasonCount(item)} ${getMetadata.getEpisodeCount(item)}`}</Text>
                    : null
                }

                { 
                    getMetadata.getRuntime(item) || getMetadata.getReleaseYear(item) ?
                        <Text 
                            style={{
                                width: this.getItemContainerWidth(),
                                textAlign: 'center'}}
                            numberOfLines={1}
                            ellipsizeMode='tail'
                            onPress={() => this.goToItemDetail(item)}
                        >{ `${getMetadata.getRuntime(item)} ${getMetadata.getReleaseYear(item)}`} </Text>
                    : null
                }
           </View>
        )
    }

    render() {
        return (
            <View style={styles.container}>
                {/* <Header 
                    title='Search' 
                    componentId={this.props.componentId}
                    centerComponent={{title: 'Search',  style: { color: 'white' } }} 
                    rightComponent={this.listTypeIcon()}
                >
                </Header> */}
                <Search
                    ref='search_box'
                    backgroundColor='white'
                    style={{ flex: 1 }}
                    titleCancelColor='black'
                    blurOnSubmit={true}
                    onChangeText={this.searchTextChanged}
                    onSearch={this.executeSearch}
                    onCancel={this.onCancel}
                />

                {
                    this.props.search.results &&
                    this.props.search.results.data &&
                    this.props.search.results.data.length === 0 ? 
                        <View style={styles.noResults}>
                            <Icon
                                name='report'
                                color='#476DC5'
                                size={55}
                            />
                            <Text> No results! </Text>
                        </View>
                    : null
                }

                {
                    this.props.search.fetching ?  
                        <View style={styles.fetching}>
                            <ActivityIndicator size='large' color='#476DC5' animating={this.props.search.fetching} /> 
                        </View>
                    : null 
                }

                <FlatList
                    data={this.getResults.call(this)}
                    renderItem={this.renderItem}
                    keyExtractor={this.keyExtractor}
                    // g = grid, l = list, h = horizontal, v = vertical
                    key={(this.state.gridView ? 'g' : 'l') + (checkDevice.isLandscape() ? 'h' : 'v')}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                    numColumns={this.state.gridView ? (checkDevice.isLandscape() ? 4 : 2) : 1}
                    columnWrapperStyle={ this.state.gridView ? {justifyContent: 'flex-start'} : null}
                />
            </View>
        );
    }
}

const mapStateToProps = (state: ReduxState) => {
    return {
        search: state.search
    };
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        addRecentlyViewed: (item: object) => {
            dispatch(SearchActions.searchAddRecent(item));
        },
        addItemToList(componentId: string, listId: string, itemId: string | number) {
            dispatch(ListActions.addToList(componentId, listId, itemId));
        },
        clearSearch: () => {
            dispatch(SearchActions.searchClear());
        },
        doSearch: (searchText: string) => {
            dispatch(SearchActions.searchRequest(searchText));
        },
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        },
        removeRecentlyViewed: (item: object) => {
            dispatch(SearchActions.searchRemoveRecent(item));
        },
        removeAllRecentlyViewed: () => {
            dispatch(SearchActions.searchRemoveAllRecent());
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SearchScreen);
