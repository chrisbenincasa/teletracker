import * as R from 'ramda';
import React, { Component } from 'react';
import { ActivityIndicator, Dimensions, FlatList, Image, Text, View, TouchableOpacity } from 'react-native';
import { Icon, ListItem } from 'react-native-elements';
import {
    Button,
    Card,
    CardActions,
    CardContent,
    CardCover,
    Chip,
    Divider,
    Title,
    Paragraph,
    TouchableRipple
} from 'react-native-paper';
import Search from 'react-native-search-box';
import { NavigationScreenProp } from 'react-navigation';
import { ApiResponse } from '../../node_modules/apisauce';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { appVersion, tracker } from '../Components/Analytics';
import checkDevice from '../Components/Helpers/checkOrientation';
import getMetadata from '../Components/Helpers/getMetadata';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import ListActions from '../Redux/ListRedux';
import SearchActions from '../Redux/SearchRedux';
import ReduxState from '../Redux/State';
import UserActions from '../Redux/UserRedux';
import { teletrackerApi } from '../Sagas';
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
    navigation: NavigationScreenProp<any>,
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
                        name={gridView ? 'view-headline' : 'apps'}
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
        this.renderList = this.renderList.bind(this);
        this.renderGrid = this.renderGrid.bind(this);
        this.markAsWatched = this.markAsWatched.bind(this);
        this.manageLists = this.manageLists.bind(this);
        this.prefillSearch = this.prefillSearch.bind(this);

        this.state = {
            orientation: checkDevice.isPortrait() ? 'portrait' : 'landscape',
            devicetype: checkDevice.isTablet() ? 'tablet' : 'phone',
            gridView: false,
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

        this.setState({ searchText: text });
// console.log(this.state.searchText);

    }

    prefillSearch(text: string) {
        console.log(text);
        this.searchTextChanged(text);
        this.executeSearch();
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
        thingPromise = new Promise((resolve, reject) => {

            if (!item && item.type && item.id) {
                let getPromise: Promise<ApiResponse<any>> = item.type === 'show' ? teletrackerApi.getShow(item.id) : teletrackerApi.getMovie(item.id);

                getPromise.then(response => {
                    if (!response.ok) {
                        this.setState({ loadError: true, loading: true });
                    } else {
                        resolve(response.data.data);
                    }
                }).catch((e) => {
                    console.tron.log('bad', e);
                    reject(e);
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

            this.props.navigation.navigate('ListManage', {
                thing: this.state.item,
                userDetails: this.state.userDetails
            });

            // This should probably happen when the manage list modal closes
            this.setState({
                visible: !this.state.visible
              });

        }).catch((e) => {
            console.tron.log(e);
        });
    }

    markAsWatched(item) {
        // Track when users mark item watched on the item details screen
        tracker.trackEvent('item-detail-action', 'mark-as-watched', {
            label: appVersion
        } );

        this.props.markAsWatched(this.props.componentId, item.id, item.type);
    }

    // The default function if no Key is provided is index
    // an identifiable key is important if you plan on
    // item reordering.  Otherwise index is fine
    // g = grid, l = list, h = horizontal, v = vertical
    keyExtractor = (item: any, index: any) => item.id + (this.state.gridView ? 'g' : 'l') + (checkDevice.isLandscape() ? 'h' : 'v');

    // How many items should be kept im memory as we scroll?
    oneScreensWorth = 8;


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

    renderEmpty = () => { 
        return (
            !this.props.search.results && !this.props.search.fetching ?
                <Card style={{flex: 1, flexDirection: 'row', margin: 8}}>
                    <View style={styles.defaultScreen}>
                        <Icon
                            name='search'
                            color='#476DC5'
                            size={75}
                            containerStyle={{height: 75, marginBottom: 20}}
                        />
                        <Text> Search for Movies, TV Shows, or People! Try it out: </Text>
                        
                        <View style={{
                            flexDirection: 'row', 
                            flexWrap: 'wrap'
                        }}>
                            <Chip onPress={() => this.prefillSearch('The Matrix')}>The Matrix</Chip>
                            <Chip onPress={() => this.prefillSearch('Halt & Catch Fire')}>Halt & Catch Fire</Chip>
                            <Chip onPress={() => this.prefillSearch('Nic Cage')}>Nic Cage</Chip>
                        </View>
                        {
                            this.props.search.recentlyViewed && this.props.search.recentlyViewed.length > 0 ? 
                            <View>
                                <Divider style={{ backgroundColor: 'grey', marginVertical: 15 }} />
                                <Text h3>Recently Viewed</Text>
                                {
                                    this.props.search.recentlyViewed.map((i) => (
                                        <ListItem
                                            roundAvatar
                                            avatar={{uri: 'https://image.tmdb.org/t/p/w154' + getMetadata.getPosterPath(i) }}
                                            key={i.id}
                                            title={i.name}
                                            onPress={() => this.goToItemDetail(i)}
                                            rightIcon={{name: 'close'}}
                                            onPressRightIcon={() => this.props.removeRecentlyViewed(i)}
                                        />
                                    ))
                                }
                                <Button
                                    raised
                                    primary
                                    style={{margin: 10}}
                                    icon='delete'
                                    onPress={() => this.props.removeAllRecentlyViewed()}
                                >
                                    Clear All Recent Searches
                                </Button>
                            </View>
                            : null
                        }
                    </View>
                </Card>
            : null 
        )
    };

    renderList ( { item }:object ) {
        return (
            <Card style={{flexDirection: 'row', margin: 8}}>
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

                        <CardContent style={{
                            flexGrow: 1
                        }}>
                            <Title style={{flex:1}}>
                                {item.name}
                            </Title>
                                {
                                getMetadata.getSeasonCount(item) || getMetadata.getEpisodeCount(item) ?
                                <Paragraph 
                                        style={{
                                            flex: 1,
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
                                        flex: 1,
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
                <CardActions style={{
                    flexDirection: 'row',
                    flexGrow: 1
                }}>
                    <Button
                        raised
                        style={{
                            flex: 1,
                            textAlign: 'center'
                        }}
                        icon={getMetadata.belongsToLists(item) ? 'visibility-off' : 'visibility'}
                        onPress={() => this.markAsWatched(item)}
                    >
                        {getMetadata.belongsToLists(item) ? 'Mark Unwatched' : 'Mark Watched'}
                    </Button>
                    <Button
                        raised
                        primary
                        style={{
                            flex: 1,
                            textAlign: 'center'
                        }}
                        icon={getMetadata.belongsToLists(item) ? 'list' : 'playlist-add'}
                        onPress={() => this.manageLists(item)}

                    >
                        {getMetadata.belongsToLists(item) ? 'Manage List' : 'Add to List'}
                    </Button>
                </CardActions>
            </Card>
        )
    }

    renderGrid ( { item }:object ) {
        return (
                <Card style={{flex: 1, margin: 8}}>
                    <TouchableRipple
                        onPress={() => this.goToItemDetail(item)}
                        activeOpacity={0.5}
                        underlayColor='#fff'
                    >
                        <View>
                            {/* Showing a blank grey space for gridView helps maintain a better aesthetic*/}
                            {getMetadata.getPosterPath(item) ? 
                                <CardCover 
                                    source={{
                                        uri: 'https://image.tmdb.org/t/p/w500' + getMetadata.getPosterPath(item)
                                    }}
                                />
                            :
                            <CardCover 
                                style={{
                                    flexGrow: 1,
                                    resizeMode: 'contain',
                                }}
                                source={{
                                    uri: 'data:image/gif;base64,R0lGODlhAQABAIAAAMLCwgAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=='
                                }}
                            /> }

                            <CardContent>
                                <Title>{truncateText(item.name, 30)}</Title>
                                { 
                                getMetadata.getSeasonCount(item) || getMetadata.getRuntime(item) || getMetadata.getReleaseYear(item) ?
                                    <Paragraph
                                        style={{
                                            width: this.getItemContainerWidth(),
                                            textAlign: 'left',
                                            fontStyle: 'italic'
                                        }}
                                    >
                                        { 
                                            `${getMetadata.getSeasonCount(item) ? getMetadata.getSeasonCount(item) : getMetadata.getRuntime(item)} ${getMetadata.getReleaseYear(item)}`
                                        }
                                    </Paragraph>
                                : null
                                }
                            </CardContent>
                        </View>
                    </TouchableRipple>
                    <CardActions style={{
                        flexDirection: 'row',
                        flexGrow: 1
                    }}>
                        <Button
                            raised
                            primary
                            style={{
                                flex: 1,
                                textAlign: 'center',
                                alignSelf: 'flex-end'
                            }}
                            icon={getMetadata.belongsToLists(item) ? 'list' : 'playlist-add'}
                            onPress={() => this.manageLists(item)}
                        >
                            {getMetadata.belongsToLists(item) ? 'Manage List' : 'Add to List'}
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
                    <Card style={{flex: 1, flexDirection: 'row', margin: 8}}>
                            
                            <View style={styles.defaultScreen}>
                                <ActivityIndicator
                                    size='large'
                                    color='#476DC5'
                                    animating={this.props.search.fetching}
                                />
                            </View> 
                        </Card>
                 :
                <FlatList
                    data={this.getResults.call(this)}
                    renderItem={this.state.gridView ? this.renderGrid : this.renderList }
                    keyExtractor={this.keyExtractor}
                    // g = grid, l = list, h = horizontal, v = vertical
                    key={(this.state.gridView ? 'g' : 'l') + (checkDevice.isLandscape() ? 'h' : 'v')}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                    numColumns={this.state.gridView ? 2 : 1}
                    // columnWrapperStyle={ this.state.gridView && checkDevice.isLandscape() ? {justifyContent: 'flex-start'} : null}
                    contentContainerStyle={{flexGrow: 1}}
                    style={{flex: 0}}
                />

            }

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
