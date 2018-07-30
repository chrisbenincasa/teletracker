import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View, ScrollView, ActivityIndicator } from 'react-native';
import { Button, Rating, Icon } from 'react-native-elements';
import Header from '../Components/Header/Header';
import ViewMoreText from 'react-native-view-more-text';
import { Navigation } from 'react-native-navigation';
import GetCast from '../Components/GetCast';
import GetSeasons from '../Components/GetSeasons';
import GetAvailability from '../Components/GetAvailability';
import GetGenres from '../Components/GetGenres';
import getMetadata from '../Components/Helpers/getMetadata';
import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { teletrackerApi } from '../Sagas';
import { tracker } from '../Components/Analytics';

import headerStyles from '../Themes/ApplicationStyles';
import styles from './Styles/ItemDetailScreenStyle';

interface Props {
    componentId: string,
    item?: Thing,
    itemType?: string,
    itemId?: string | number,
    addItemToList: (componentId: string, listId: string, itemId: string | number) => any,
    markAsWatched: (componentId: string, itemId: string | number, itemType: string) => void
}

type State = {
    inList: boolean,
    loading: boolean,
    loadError: boolean,
    item?: Thing
}

class ItemDetailScreen extends Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.markAsWatched = this.markAsWatched.bind(this);
        this.addItem = this.addItem.bind(this);

        this.state = {
            inList: false,
            loading: true,
            loadError: false,
            item: props.item
        };
        
        // Disable menu swipe out on ItemDetailScreen
        Navigation.mergeOptions(this.props.componentId, {
            sideMenu: {
                left: {
                    visible: false,
                    enabled: false
                }
            }
        });
    }

    componentDidMount() {
        tracker.trackScreenView('Item Detail');
        if (!this.props.item && this.props.itemType && this.props.itemId) {
            if (this.props.itemType === 'show') {
                teletrackerApi.getShow(this.props.itemId).then(response => {
                    if (!response.ok) {
                        this.setState({ loadError: true, loading: true });
                    } else {
                        this.setState({ loading: false, item: response.data.data });
                    }
                });
            } else if (this.props.itemType === 'movie') {
                teletrackerApi.getMovie(this.props.itemId).then(response => {
                    if (!response.ok) {
                        this.setState({ loadError: true, loading: true });
                    } else {
                        this.setState({ loading: false, item: response.data.data });
                    }
                });
            }
        } else if (this.props.item) {
            this.setState({ loading: false });
        }
    }

    addItem() {
        // Track when users add an item on the item details screen
        tracker.trackEvent('item-detail-action', 'add-item');

        this.props.addItemToList(this.props.componentId, 'default', this.state.item.id);

        this.setState({
            inList: !this.state.inList
          });
    }

    markAsWatched() {
        // Track when users mark item watched on the item details screen
        tracker.trackEvent('item-detail-action', 'mark-as-watched');

        this.props.markAsWatched(this.props.componentId, this.state.item.id, this.state.item.type);
    }

    renderViewMore(onPress) {
        return (
            <Text 
                onPress={onPress}
                style={{
                    textDecorationLine: 'underline',
                    paddingTop: 5
            }}>
                View more
            </Text>
        )
      }

    renderViewLess(onPress) {
        return (
            <Text 
                onPress={onPress} 
                style={{
                    textDecorationLine: 'underline', 
                    paddingTop: 5
            }}>
                View less
            </Text>
        )
    }

    render () {
        return (
            <View style={styles.container}>
                <Header
                    outerContainerStyles={{
                        backgroundColor: 'transparent',
                        position: 'absolute',
                        zIndex: 999999
                    }}
                    statusBarProps={headerStyles.header.statusBarProps}
                    componentId={this.props.componentId}
                    leftComponent={{
                        icon: 'chevron-left',
                        back: true,
                        style: {
                            color: 'white'
                        }
                    }}
                    centerComponent={null} 
                    rightComponent={null}
                />
                { this.state.loading ? (
                    <ActivityIndicator />
                ) : (
                    <ScrollView>
                        <KeyboardAvoidingView behavior='position'>
                            <View style={styles.coverContainer} >
                                {   // Check if cover image exists, otherwise show blue
                                    getMetadata.getBackdropImagePath(this.state.item) ?
                                        <Image source={{
                                            uri: 'https://image.tmdb.org/t/p/w500' + getMetadata.getBackdropImagePath(this.state.item)
                                        }}
                                            style={styles.coverImage}
                                        />
                                        : <View style={styles.emptyCoverImage}></View>
                                }
                            </View>
                            <View style={styles.subHeaderContainer}>
                                {getMetadata.getPosterPath(this.state.item)
                                    ?
                                    <Image
                                        source={{
                                            uri: 'https://image.tmdb.org/t/p/w92' + getMetadata.getPosterPath(this.state.item)
                                        }}
                                        style={styles.posterImage} />
                                    :
                                    <View style={styles.posterImage}>
                                        <Icon
                                            name='image'
                                            color='#fff'
                                            size={50}
                                            containerStyle={{ flex: 1 }}
                                        />
                                    </View>
                                }
                                <View style={styles.itemDetailsContainer}>
                                    <Text style={{
                                        marginTop: 10,
                                        marginLeft: 10,
                                        fontSize: 20
                                    }}>
                                        {this.state.item.name}
                                        {
                                            getMetadata.getReleaseYear(this.state.item)
                                                ? (
                                                    <Text>({getMetadata.getReleaseYear(this.state.item)})</Text>
                                                )
                                                : null
                                        }
                                    </Text>
                                    <View style={styles.ratingsContainer}>
                                        <Rating
                                            type="star"
                                            fractions={1}
                                            startingValue={getMetadata.getRatingPath(this.state.item) / 2}
                                            readonly
                                            imageSize={15}
                                            style={{
                                                paddingBottom: 15,
                                                marginLeft: 10
                                            }}
                                        />
                                        <Text style={styles.ratingCount}>
                                            ({getMetadata.getVoteCount(this.state.item)})
                                        </Text>
                                    </View>
                                </View>
                            </View>
                        </KeyboardAvoidingView>

                        <View style={styles.descriptionContainer}>
                            <ViewMoreText
                                numberOfLines={4}
                                renderViewMore={this.renderViewMore}
                                renderViewLess={this.renderViewLess}>
                                <Text>
                                    {
                                        getMetadata.getDescription(this.state.item)
                                    }
                                </Text>
                            </ViewMoreText>
                        </View>

                        <GetGenres item={ this.state.item } />

                        <View style={styles.buttonsContainer}>
                            <Button
                                title='Mark as Watched'
                                icon={{
                                    name: 'watch',
                                    size: 25,
                                    color: 'white'
                                }}
                                onPress={this.markAsWatched}
                                containerStyle={{ marginHorizontal: 0 }}>
                            </Button>
                            <Button
                                title={this.state.inList ? 'Remove' : 'Track'}
                                onPress={this.addItem}
                                icon={{
                                    name: this.state.inList ? 'clear' : 'add',
                                    size: 25,
                                    color: 'white'
                                }}
                                buttonStyle={{
                                    backgroundColor: this.state.inList ? 'red' : 'green'
                                }}
                                containerStyle={{ marginHorizontal: 0 }}>
                            </Button>

                        </View>

                        <GetSeasons item={ this.state.item }/>
                        <GetAvailability item={ this.state.item } />
                        <GetCast item={ this.state.item }/>
                    </ScrollView>
                ) }
            </View>
        )
    }
}

const mapStateToProps = (state) => {
    return {
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        addItemToList(componentId: string, listId: string, itemId: string | number) {
            dispatch(ListActions.addToList(componentId, listId, itemId));
        },
        markAsWatched(componentId: string, itemId: string | number, itemType: string) {
            dispatch(UserActions.postEvent(componentId, 'MarkedAsWatched', itemType, itemId));
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
