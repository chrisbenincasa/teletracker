import React from 'react';
import { SectionList, Text, View, FlatList } from 'react-native';
import { ListItem } from 'react-native-elements';
import { connect, Dispatch } from 'react-redux';

import * as R from 'ramda'
import Header from '../Components/Header/Header';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import NavActions from '../Redux/NavRedux';
import ReduxState from '../Redux/State';
import UserActions, { UserState } from '../Redux/UserRedux';
import styles from './Styles/ItemListStyle';

// More info here: https://facebook.github.io/react-native/docs/sectionlist.html

interface Props {
    componentId: string
    user: UserState
    loadUserSelf: (componentId: string) => any
}

class ItemList extends React.PureComponent<Props> {

    state = {};

    componentWillMount() {
        this.props.loadUserSelf(this.props.componentId);
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
    
    goToItemDetail(item) {
        let view = R.mergeDeepRight(NavigationConfig.DetailView, {
            component: {
                passProps: { item }
            }
        });

        this.props.pushState(this.props.componentId, view);
    }

    goToSearch() {
        
    }
    
    renderItem ({section, item}) {
        return (
            <ListItem 
                key={this.keyExtractor}
                title={item.name}
                leftIcon={{name: item.type}}
                subtitle={item.platform}
                onPress={() => this.goToItemDetail(item)} 
            />
        )
    }

    /* ***********************************************************
    * STEP 2
    * Consider the configurations we've set below.  Customize them
    * to your liking!  Each with some friendly advice.
    *
    * Removing a function here will make SectionList use default
    *************************************************************/
    
    // Show this when data is empty
    renderEmpty = () => <Text style={styles.label}> Empty list, yo! </Text>;
    
    
    // The default function if no Key is provided is index
    // an identifiable key is important if you plan on
    // item reordering.  Otherwise index is fine
    keyExtractor: (item: any, index: any) => number = (_, index) => index;
    
    // How many items should be kept im memory as we scroll?
    oneScreensWorth = 20;
    
    // extraData is for anything that is not indicated in data
    // for instance, if you kept "favorites" in `this.state.favs`
    // pass that in, so changes in favorites will cause a re-render
    // and your renderItem will have access to change depending on state
    // e.g. `extraData`={this.state.favs}
    
    // Optimize your list if the height of each item can be calculated
    // by supplying a constant height, there is no need to measure each
    // item after it renders.  This can save significant time for lists
    // of a size 100+
    // e.g. itemLayout={(data, index) => (
    //   {length: ITEM_HEIGHT, offset: ITEM_HEIGHT * index, index}
    // )}
    
    render () {
        return (
            <View style={styles.container}>
                <Header 
                    componentId={this.props.componentId} 
                    centerComponent={{title: 'My List',  style: { color: 'white' } }}  />
                <FlatList
                    renderSectionHeader={this.renderSectionHeader}
                    sections={this.getListSections.call(this)}
                    contentContainerStyle={styles.listContent}
                    data={this.props.user.details.lists[0].things}
                    renderItem={this.renderItem.bind(this)}
                    keyExtractor={this.keyExtractor}
                    initialNumToRender={this.oneScreensWorth}
                    ListHeaderComponent={this.renderHeader}
                    ListEmptyComponent={this.renderEmpty}
                />
            </View>
        )
    }
};

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

export default connect(mapStateToProps, mapDispatchToProps)(ItemList);
