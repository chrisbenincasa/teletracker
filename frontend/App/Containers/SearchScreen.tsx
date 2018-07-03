import * as R from 'ramda';
import React, { Component } from 'react';
import { Platform, ScrollView, View, FlatList, Text } from 'react-native';
import { ListItem } from 'react-native-elements';
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

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    executeSearch() {
        this.props.doSearch(this.state.searchText);
    }

    searchTextChanged(text: string) {
        return this.setState({ searchText: text });
    }

    renderEmpty = () => <Text style={styles.label}> No results! </Text>;

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
            <ListItem 
                key={this.keyExtractor}
                title={item.name}
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
                <FlatList
                    contentContainerStyle={styles.listContent}
                    data={this.getResults.call(this)}
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
