import * as R from 'ramda';
import React, { Component } from 'react';
import { ActivityIndicator, Dimensions, FlatList, Image, Text, TouchableHighlight, View, TouchableOpacity } from 'react-native';
import { Icon, ListItem } from 'react-native-elements';
import {
    Button,
    Card,
    CardActions,
    CardContent,
    CardCover,
    Divider,
    Title,
    Paragraph,
    TouchableRipple
} from 'react-native-paper';
import Search from 'react-native-search-box';
import { NavigationScreenProp } from 'react-navigation';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import { appVersion, tracker } from '../Components/Analytics';
import checkDevice from '../Components/Helpers/checkOrientation';
import getMetadata from '../Components/Helpers/getMetadata';
import { AddToListModalOptions } from './AddToListModal';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import ListActions from '../Redux/ListRedux';
import SearchActions from '../Redux/SearchRedux';
import ReduxState from '../Redux/State';
import UserActions from '../Redux/UserRedux';
import { teletrackerApi } from '../Sagas';
import { tracker, appVersion } from '../Components/Analytics';
import { truncateText } from '../Components/Helpers/textHelper';

import { Colors } from './../Themes/'; //testing only, cleanup later
import styles from './Styles/SearchScreenStyle';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    addRecentlyViewed: (item: object) => any,
    clearSearch: () => any,
    componentId: string,
    doSearch: (search: String) => any,
    loadUserSelf: (componentId: string) => any,
    markAsWatched: (componentId: string, itemId: string | number, itemType: string) => void,
    removeAllRecentlyViewed: () => any,
    removeRecentlyViewed: (item: object) => any,
    search: any
}

interface State {
    devicetype: string,
    gridView: boolean,
    loading: boolean,
    loadError: boolean,
    orientation: string,
    searchText: string,
    visible: false
}

class SearchScreen extends Component<Props, State> {

    static drawerOptions = {
        enabled: true
    }

    static navigationOptions = ({ navigation }: { navigation: NavigationScreenProp<any> }) => {
        const gridView = navigation.getParam('gridView');
        const changeView = navigation.getParam('changeView');

        return {
            title: 'Search',
            headerRight: (
                <TouchableOpacity style={{ marginHorizontal: 10 }}>    
                    <Icon
                        name={gridView ? 'list' : 'apps'}
                        color={Colors.white}
                        underlayColor={Colors.headerBackground}
                        onPress={changeView}
                    />
                </TouchableOpacity>
            )
        };
    };

    constructor(props: Props) {
        super(props);

        this.searchTextChanged = this.searchTextChanged.bind(this);
        this.executeSearch = this.executeSearch.bind(this);
        this.renderItem = this.renderItem.bind(this);
        this.markAsWatched = this.markAsWatched.bind(this);
        this.manageLists = this.manageLists.bind(this);

        this.state = {
            orientation: checkDevice.isPortrait() ? 'portrait' : 'landscape',
            devicetype: checkDevice.isTablet() ? 'tablet' : 'phone',
            gridView: true,
            searchText: null
        };

        // Event Listener for orientation changes
        Dimensions.addEventListener('change', () => {
            this.setState({
                orientation: checkDevice.isPortrait() ? 'portrait' : 'landscape'
            });
        });
    }

    private tvResultsLens = R.lensPath(['search', 'results', 'data']);

    changeView() {
        this.setState({  gridView: !this.state.gridView });

        this.props.navigation.setParams({ gridView: !this.state.gridView });
    }

    listTypeIcon() {
        return (
            <Icon 
                name={this.state.gridView ? 'list' : 'apps'}
                color={Colors.white}
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
        this.props.navigation.setParams({ 
            changeView: this.changeView.bind(this),
            gridView: this.state.gridView
        });
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
    
    componentDidMount() {
        tracker.trackScreenView('Search');
    }

    manageLists(item) {
        // Track when users add an item on the item details screen
        tracker.trackEvent('item-detail-action', 'open-list-manager', {
            label: appVersion
        });


        let thingPromise: Promise<any>;
        let userDetailsPromise: Promise<any>;

        userDetailsPromise = new Promise((resolve) => {
            if (!this.state.userDetails) {
                teletrackerApi.getThingUserDetails(item.id).then(userDetails => {
                    if (!userDetails.ok) {
                        this.setState({ loadError: true, loading: true });
                    } else {
                        resolve(userDetails.data.data);
                    }
                });
            } else {
                resolve(this.state.userDetails);
            }
        })

        // If we have no item, load it
        thingPromise = new Promise((resolve) => {
            
            if (!item && item.type && item.id) {
                let func: (item: object) => Promise<ApiResponse<any>> = item.type === 'show' ? teletrackerApi.getShow : teletrackerApi.getMovie;

                func(item.id).then(response => {
                    if (!response.ok) {
                        this.setState({ loadError: true, loading: true });
                    } else {
                        resolve(response.data.data);
                    }
                });
            } else if (item) {
                resolve(item);
            }
        });

        Promise.all([
            thingPromise,
            userDetailsPromise
        ]).then(([thing, userDetails]) => {
            this.setState({
                item: thing,
                userDetails,
                loading: false
            });

            Navigation.showModal({
                stack: {
                    children: [{
                        component: {
                            name: 'navigation.main.AddToListModal',
                            passProps: {
                                thing: item,
                                userDetails: this.state.userDetails
                            },
                            options: AddToListModalOptions
                        }
                    }]
                }
            });
        })

        this.setState({
            visible: !this.state.visible
          });
    }

    markAsWatched(item) {
        // Track when users mark item watched on the item details screen
        tracker.trackEvent('item-detail-action', 'mark-as-watched', {
            label: appVersion
        } );

        this.props.markAsWatched(this.props.componentId, item.id, item.type);
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

        this.props.navigation.navigate('DetailScreen', { item });
        // Navigation.push(this.props.componentId, view);
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
            <Card style={{flex: 1, margin: 8}}>
                <TouchableRipple
                    onPress={() => this.goToItemDetail(item)}
                    activeOpacity={0.5}
                    underlayColor='#fff'
                >
                    <View>
                        {/* Showing a blank grey space for gridView helps maintain a better aesthetic*/}
                        {getMetadata.getBackdropImagePath(item) || this.state.orientation === 'landscape' ? 
                            <CardCover 
                                source={{uri: 'https://image.tmdb.org/t/p/w500' + getMetadata.getBackdropImagePath(item)}}
                            />
                        : null }

                        <CardContent style={{flex: 1}}>
                            <Title style={{flex: 1}}>{item.name}</Title>
                                {
                                getMetadata.getSeasonCount(item) || getMetadata.getEpisodeCount(item) ?
                                <Paragraph 
                                        style={{
                                            width: this.getItemContainerWidth(),
                                            textAlign: 'left', 
                                            fontStyle: 'italic'
                                        }}
                                    >
                                        {
                                            `${getMetadata.getSeasonCount(item)} ${getMetadata.getEpisodeCount(item)}`
                                        }
                                    </Paragraph>
                                : null
                            }
                            { 
                            getMetadata.getRuntime(item) || getMetadata.getReleaseYear(item) ?
                                <Paragraph
                                    style={{
                                        width: this.getItemContainerWidth(),
                                        textAlign: 'left',
                                        fontStyle: 'italic'
                                    }}
                                >
                                    { 
                                        `${getMetadata.getRuntime(item)} ${getMetadata.getReleaseYear(item)}`
                                    }
                                </Paragraph>
                            : null
                            }
                        </CardContent>
                        <CardContent>   
                            <Paragraph>
                                {
                                    truncateText(getMetadata.getDescription(item), 250)
                                }
                            </Paragraph>
                        </CardContent>
                    </View>
                </TouchableRipple>
                <CardActions style={{flex: 2}}>
                    <Button
                        raised
                        style={{
                            flex: 1,
                            textAlign: 'center'
                        }}
                        icon={getMetadata.belongsToLists(item) ? 'visibility-off' : 'visibility'}
                        onPress={() => this.markAsWatched(item)}
                    >
                        {getMetadata.belongsToLists(item) ? 'Mark as Unwatched' : 'Mark as Watched'}
                    </Button>
                    <Button
                        raised
                        style={{
                            flex: 1,
                            textAlign: 'center'
                        }}
                        icon={getMetadata.belongsToLists(item) ? 'list' : 'playlist-add'}
                        onPress={() => this.manageLists(item)}

                    >
                        {getMetadata.belongsToLists(item) ? 'Manage Tracking' : 'Add to List'}
                    </Button>
                </CardActions>
            </Card>
        )
    }

    render() {
        return (
            <View style={styles.container}>
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
                    numColumns={this.state.gridView && checkDevice.isLandscape() ? 2 : 1}
                    columnWrapperStyle={ this.state.gridView && checkDevice.isLandscape() ? {justifyContent: 'flex-start'} : null}
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
        clearSearch: () => {
            dispatch(SearchActions.searchClear());
        },
        doSearch: (searchText: string) => {
            dispatch(SearchActions.searchRequest(searchText));
        },
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        },
        markAsWatched(componentId: string, itemId: string | number, itemType: string) {
            dispatch(UserActions.postEvent(componentId, 'MarkedAsWatched', itemType, itemId));
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
