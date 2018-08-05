import React, { Component } from 'react';
import { ActivityIndicator, Image, KeyboardAvoidingView, ScrollView, Text, View } from 'react-native';
import { Button, Icon, Rating } from 'react-native-elements';
import ViewMoreText from 'react-native-view-more-text';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import { NavigationScreenProp, NavigationScreenOptions } from 'react-navigation';
import { ApiResponse } from '../../node_modules/apisauce';
import { appVersion, tracker } from '../Components/Analytics';
import GetAvailability from '../Components/GetAvailability';
import GetCast from '../Components/GetCast';
import GetGenres from '../Components/GetGenres';
import GetSeasons from '../Components/GetSeasons';
import getMetadata from '../Components/Helpers/getMetadata';
import { Thing } from '../Model/external/themoviedb';
import ItemActions from '../Redux/ItemRedux';
import UserActions from '../Redux/UserRedux';
import { teletrackerApi } from '../Sagas';
import Colors from '../Themes/Colors';
import styles from './Styles/ItemDetailScreenStyle';


interface Props {
    componentId: string,
    item?: Thing,
    itemType?: string,
    itemId?: string | number,
    markAsWatched: (componentId: string, itemId: string | number, itemType: string) => void,
    fetchShow: (id: string | number) => any
    navigation: NavigationScreenProp<any>
}

type State = {
    inList: boolean,
    loading: boolean,
    loadError: boolean,
    item?: Thing,
    userDetails?: any
}

class ItemDetailScreen extends Component<Props, State> {
    static navigationOptions: NavigationScreenOptions = {
        header: null
    }

    constructor(props: Props) {
        super(props);
        this.markAsWatched = this.markAsWatched.bind(this);
        this.manageLists = this.manageLists.bind(this);

        this.state = {
            inList: false,
            loading: true,
            loadError: false,
            item: props.item
        };
    }

    componentDidMount() {
        tracker.trackScreenView('Item Detail');

        let thingPromise: Promise<any>;
        let userDetailsPromise: Promise<any>;

        userDetailsPromise = new Promise((resolve) => {
            if (!this.state.userDetails) {
                teletrackerApi.getThingUserDetails(this.props.itemId || this.props.item.id).then(userDetails => {
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
            if (!this.props.item && this.props.itemType && this.props.itemId) {
                let getPromise: Promise<ApiResponse<any>> = this.props.itemType === 'show' ? teletrackerApi.getShow(this.props.itemId) : teletrackerApi.getMovie(this.props.itemId);

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
            } else if (this.props.item) {
                resolve(this.props.item);
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
            })
        }).catch((e) => {
            console.tron.log(e);
        })
    }

    manageLists() {
        // Track when users add an item on the item details screen
        tracker.trackEvent('item-detail-action', 'add-item', {
            label: appVersion
        });

        this.props.navigation.navigate('ListManage', {
            thing: this.state.item,
            userDetails: this.state.userDetails
        });
    }

    markAsWatched() {
        // Track when users mark item watched on the item details screen
        tracker.trackEvent('item-detail-action', 'mark-as-watched', {
            label: appVersion
        } );

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
                                title={this.state.userDetails.belongsToLists.length > 0 ? 'Manage Tracking' : 'Track'}
                                onPress={this.manageLists}
                                icon={{
                                    name: this.state.userDetails.belongsToLists.length > 0 ? 'clipboard' : 'add',
                                    type: this.state.userDetails.belongsToLists.length > 0 ? 'entypo' : null,
                                    size: 25,
                                    color: 'white'
                                }}
                                buttonStyle={{
                                    backgroundColor: this.state.userDetails.belongsToLists.length > 0 ? Colors.headerBackground : 'green'
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
        markAsWatched(componentId: string, itemId: string | number, itemType: string) {
            dispatch(UserActions.postEvent(componentId, 'MarkedAsWatched', itemType, itemId));
        },
        fetchShow(id: string | number) {
            dispatch(ItemActions.fetchShow(id))
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
