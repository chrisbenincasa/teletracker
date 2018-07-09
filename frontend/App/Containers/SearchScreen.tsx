import * as R from 'ramda';
import React, { Component } from 'react';
import { 
    Platform, 
    ScrollView, 
    View, 
    FlatList, 
    Text, 
    Image, 
    TouchableHighlight, 
    ActivityIndicator,
    Dimensions
} from 'react-native';
import { Icon, ListItem, Button } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import Search from 'react-native-search-box';
import checkDevice from '../Components/Helpers/checkOrientation';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import Header from '../Components/Header/Header';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import SearchActions from '../Redux/SearchRedux';
import ReduxState from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';

import { Colors } from './../Themes/'; //testing only, cleanup later

import styles from './Styles/SearchScreenStyle';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    componentId: string;
    search: any;
    doSearch: (search: String) => any;
    clearSearch: () => any;
    addRecentlyViewed: (item: object) => any;
    removeRecentlyViewed: (item: object) => any;
    removeAllRecentlyViewed: () => any;
    loadUserSelf: (componentId: string) => any;
}

interface State {
    searchText: string;
    orientation: string;
    devicetype: string;
    gridView: boolean;
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

    getImagePath(item: object) {
        if (this.hasTmdbMovie(item)) {
            // This throws a lens error pretty consistantly, requires further investigation.  Workaround in place for now.
            // return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
            return item.metadata.themoviedb.movie.poster_path;
        } else if (this.hasTmdbShow(item)) {
            return item.metadata.themoviedb.show.poster_path;
        } else if (this.hasTmdbPerson(item)) {
            return; // There are no photos for people yet
        }
    }

    hasTmdbMetadata(item: object) {
        return item.metadata && item.metadata.themoviedb;
    }

    hasTmdbMovie(item: object) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.movie;
    }

    hasTmdbShow(item: object) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.show;
    }
  
    hasTmdbPerson(item: object) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.person;
    }

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    executeSearch() {
        this.props.doSearch(this.state.searchText);
    }

    // Important: You must return a Promise
    onCancel = () => {
        return new Promise((resolve, reject) => {
            this.props.clearSearch(this.state.searchText);
            resolve();
        });
    }

    searchTextChanged(text: string) {
        return this.setState({ searchText: text });
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
                </View>
            : null 
        )
    };

    // The default function if no Key is provided is index
    // an identifiable key is important if you plan on
    // item reordering.  Otherwise index is fine
    // keyExtractor: (item: any, index: any) => number = (_, index) => index;
    // keyExtractor: (item: any, index: any) => number = ({item}) => (item.id);
    keyExtractor: (item: object) => item.id;

    // How many items should be kept im memory as we scroll?
    // oneScreensWorth = { checkDevice.isLandscape() ? 5 : 3 };
    oneScreensWorth = 18;


    goToItemDetail(item: object) {
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

    renderItem ( { item }: object) {
        return (
            <View style={{margin: 5}}>
                <TouchableHighlight 
                    activeOpacity={0.3}
                    onPress={() => this.goToItemDetail(item)
                }>
                    <View>
                        { this.getImagePath(item) ?
                            <Image
                                style={styles.posterContainer}
                                source={{uri: 'https://image.tmdb.org/t/p/w154' + this.getImagePath(item) }}
                            /> : 
                            <View style={styles.posterContainer}>
                                <Icon name='image' color='#fff' size={50} containerStyle={{flex: 1}}/>
                            </View>
                        }
                    </View>
                </TouchableHighlight>
                <Text 
                    style={{width: 92}}
                    numberOfLines={1}
                    ellipsizeMode='tail'
                    onPress={() => this.goToItemDetail(item)}
                >{item.name}</Text>
           </View>
        )
    }

    render() {
        return (
            <View style={styles.container}>
                <Header 
                    title='Search' 
                    componentId={this.props.componentId}
                    centerComponent={{title: 'Search',  style: { color: 'white' } }} 
                    rightComponent={this.listTypeIcon()}
                />
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
                        this.props.search.recentlyViewed && this.props.search.recentlyViewed.length > 0 ? 
                        <View>
                            <Text h3>Recently Viewed</Text>
                            {this.props.search.recentlyViewed.map((i) => (
                                <ListItem
                                    roundAvatar
                                    avatar={{uri: 'https://image.tmdb.org/t/p/w154' + this.getImagePath(i) }}
                                    key={i.id}
                                    title={i.name}
                                    // onPress={() => this.goToItemDetail(i)}
                                    rightIcon={{name: 'close'}}
                                    onPressRightIcon={() => this.props.removeRecentlyViewed(i)}
                                />
                            ))}
                            <Button
                                raised
                                icon={{name: 'delete'}}
                                title='Clear All Recent Searches'
                                onPress={() => this.props.removeAllRecentlyViewed()}
                            />
                        </View>
                        : null
                    }
                

                {
                    this.props.search.results && this.props.search.results.data && this.props.search.results.data.length === 0 ? 
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
                    // g = grid, l = list
                    // h = horizontal, v = vertical
                    key={this.keyExtractor + (this.state.gridView ? 'g' : 'l') + (checkDevice.isLandscape() ? 'h' : 'v')}
                    // key={this.keyExtractor + (checkDevice.isLandscape() ? 'h' : 'v')}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                    numColumns={this.state.gridView ? (checkDevice.isLandscape() ? 5 : 3) : 1}
                    // numColumns={checkDevice.isLandscape() ? 5 : 3}
                    columnWrapperStyle={ this.state.gridView ? {justifyContent: 'center'} : null}
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
        doSearch: (searchText: string) => {
            dispatch(SearchActions.searchRequest(searchText));
        },
        clearSearch: () => {
            dispatch(SearchActions.searchClear());
        },
        addRecentlyViewed: (item: object) => {
            dispatch(SearchActions.searchAddRecent(item));
        },
        removeRecentlyViewed: (item: object) => {
            dispatch(SearchActions.searchRemoveRecent(item));
        },
        removeAllRecentlyViewed: () => {
            dispatch(SearchActions.searchRemoveAllRecent());
        },
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SearchScreen);
