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
    ActivityIndicator 
} from 'react-native';
import { Icon } from 'react-native-elements';
import { Navigation } from 'react-native-navigation';
import Search from 'react-native-search-box';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import Header from '../Components/Header/Header';
import { NavigationConfig } from '../Navigation/NavigationConfig';
import SearchActions from '../Redux/SearchRedux';
import ReduxState from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';

import styles from './Styles/SearchScreenStyle';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    componentId: string;
    search: any;
    doSearch: (search: String) => any;
    loadUserSelf: (componentId: string) => any;
}

interface State {
    searchText: string;
}

class SearchScreen extends Component<Props, State> {

    private tvResultsLens = R.lensPath(['search', 'results', 'data']);

    getImagePath(item) {
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

    hasTmdbMetadata(item) {
        return item.metadata && item.metadata.themoviedb;
    }

    hasTmdbMovie(item) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.movie;
    }

    hasTmdbShow(item) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.show;
    }
  
    hasTmdbPerson(item) {
        return this.hasTmdbMetadata(item) && item.metadata.themoviedb.person;
    }

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    executeSearch() {
        this.props.doSearch(this.state.searchText);
    }

    searchTextChanged(text: string) {
        return this.setState({ searchText: text });
    }

    // This is currently always displayed, To Do: need to hide on initial load
    renderEmpty = () => (
        <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
            <Icon
                name='report'
                color='#476DC5'
                size={55}
            />
            <Text style={styles.label}> No results! </Text>
        </View>
    );

    // The default function if no Key is provided is index
    // an identifiable key is important if you plan on
    // item reordering.  Otherwise index is fine
    keyExtractor: (item: any, index: any) => number = (_, index) => index;
    
    // How many items should be kept im memory as we scroll?
    oneScreensWorth = 20;

    goToItemDetail(item: any) {
        const view = R.mergeDeepLeft(NavigationConfig.DetailView, {
            component: {
                passProps: { item }
            }
        });
        Navigation.push(this.props.componentId, view);
    }

    getResults() {
        const tvResults = R.view(this.tvResultsLens, this.props);
        if (tvResults && tvResults.length > 0 ) {
            return tvResults;
        } else {
            return [];
        }
    }

    renderItem ({item}) {
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
                                source={{uri: "https://image.tmdb.org/t/p/w154" + this.getImagePath(item) }}
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
                    title="Search" 
                    componentId={this.props.componentId}
                    centerComponent={{title: 'Search',  style: { color: 'white' } }} 
                />
                <Search
                    ref="search_box"
                    backgroundColor="white"
                    style={{ flex: 1 }}
                    titleCancelColor="black"
                    blurOnSubmit={true}
                    onChangeText={this.searchTextChanged.bind(this)}
                    onSearch={this.executeSearch.bind(this)}
                />

                {   this.props.search.fetching ?  
                        <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
                            <ActivityIndicator size="large" color="#476DC5" animating={this.props.search.fetching} /> 
                        </View>
                    : null 
                }
                
                <FlatList
                    contentContainerStyle={styles.listContent}
                    data={this.getResults.call(this)}
                    renderItem={this.renderItem.bind(this)}
                    keyExtractor={this.keyExtractor}
                    initialNumToRender={this.oneScreensWorth}
                    ListEmptyComponent={this.renderEmpty}
                    numColumns={3}
                    columnWrapperStyle={{marginHorizontal: 8}}
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
        loadUserSelf: (componentId: string) => {
            dispatch(UserActions.userSelfRequest(componentId));
        }
    };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SearchScreen);
