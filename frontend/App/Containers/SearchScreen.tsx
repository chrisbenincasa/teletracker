import * as R from 'ramda';
import React, { Component } from 'react';
import { Platform, ScrollView, View } from 'react-native';
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

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
    }

    executeSearch() {
        this.props.doSearch(this.state.searchText);
    }

    searchTextChanged(text: string) {
        return this.setState({ searchText: text });
    }

    goToItemDetail(item: any) {
        const view = R.mergeDeepLeft(NavigationConfig.DetailView, {
            component: {
                passProps: { item }
            }
        });
        Navigation.push(this.props.componentId, view);
    }

    private tvResultsLens = R.lensPath(['search', 'results']);

    render() {
        const tvResults = R.view(this.tvResultsLens, this.props);

        return (
            <View style={styles.container}>
                <Header 
                    title="Search" 
                    componentId={this.props.componentId} 
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
                {tvResults && tvResults.total_results > 0 ? (
                    <ScrollView>
                        {tvResults.results.map((item, i) => (
                            <ListItem
                                key={i}
                                title={item.title || item.name}
                                subtitle={'Type: ' + item.media_type}
                                onPress={() => this.goToItemDetail(item)}
                            />
                        ))}
                    </ScrollView>
                ) : null}
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
