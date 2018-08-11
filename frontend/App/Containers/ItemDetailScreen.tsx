import React, { Component } from 'react';
import { ActivityIndicator, Image, ScrollView, Text, View } from 'react-native';
import { Rating } from 'react-native-elements';
import { Card, CardContent, FABGroup, Provider as PaperProvider, Provider as PaperProvider } from 'react-native-paper';
import ViewMoreText from 'react-native-view-more-text';
import { NavigationScreenOptions, NavigationScreenProp } from 'react-navigation';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

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
import { PaperTheme, PaperTheme, Colors } from '../Themes/';
import styles from './Styles/ItemDetailScreenStyle';

interface Props {
    componentId: string,
    fetchShow: (id: string | number) => any,
    item?: Thing,
    itemId?: string | number,
    itemType?: string,
    markAsWatched: (componentId: string, itemId: string | number, itemType: string) => void,
    navigation: NavigationScreenProp<any>,
    open: boolean,
    // visible: boolean
}

type State = {
    loading: boolean,
    loadError: boolean,
    item?: Thing,
    open: boolean,
    userDetails?: any,
    visible: boolean
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
            loading: true,
            loadError: false,
            item: props.item,
            open: false,
            visible: false
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
        tracker.trackEvent('item-detail-action', 'open-list-manager', {
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
            <PaperProvider theme={PaperTheme}>
                <View style={styles.container}>
                    { this.state.loading ? (
                        <Card style={{
                            margin: 15,
                            flex: 1,
                            flexDirection: 'row',
                            alignItems: 'center'
                        }}>
                            <View style={{
                                flex: 1,
                                flexDirection: 'column',
                                justifyContent: 'center',
                                alignItems: 'center'
                            }}>
                                <ActivityIndicator
                                    size='large'
                                />
                            </View>
                        </Card>
                    ) : (
                        <ScrollView>
                            {   // Check if cover image exists, otherwise don't show a cover image
                                getMetadata.getBackdropImagePath(this.state.item) ?
                                    <View style={styles.coverContainer} >
                                        <Image source={{
                                            uri: 'https://image.tmdb.org/t/p/w500' + getMetadata.getBackdropImagePath(this.state.item)
                                        }}
                                            style={styles.coverImage}
                                        />
                                    </View>
                                : null
                            }
                            <View style={styles.itemDetailsContainer}>
                                <Text style={{
                                    flex: 1, 
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
                                        type='star'
                                        ratingBackgroundColor={Colors.transparent}
                                        fractions={1}
                                        startingValue={
                                            getMetadata.getRatingPath(this.state.item) / 2
                                        }
                                        readonly
                                        imageSize={15}
                                    />
                                    <Text style={styles.ratingCount}>
                                        ({getMetadata.getVoteCount(this.state.item)})
                                    </Text>
                                </View>
                            </View>

                            <Card style={styles.descriptionContainer}>
                                <CardContent style={{flex: 1}}>
                                    <View style={{
                                        flexDirection: 'row',
                                        flex: 1
                                    }}>
                                        {
                                            getMetadata.getPosterPath(this.state.item) ?
                                                <Image
                                                    source={{
                                                        uri: 'https://image.tmdb.org/t/p/w92' + getMetadata.getPosterPath(this.state.item)
                                                    }}
                                                    style={{
                                                        width: 92,
                                                        height: 138, 
                                                        backgroundColor: '#C9C9C9'
                                                    }}
                                                />
                                            : null
                                        }
                                        <ViewMoreText
                                            numberOfLines={6}
                                            renderViewMore={this.renderViewMore}
                                            renderViewLess={this.renderViewLess}
                                            style={{width: 150}}
                                        >
                                            {
                                                getMetadata.getDescription(this.state.item)
                                            }
                                        </ViewMoreText>
                                    </View>
                                    <GetGenres item={ this.state.item } />
                                </CardContent>
                            </Card>

                            <GetSeasons item={ this.state.item }/>
                            <GetAvailability item={ this.state.item } />
                            <GetCast item={ this.state.item }/>

                            <View style={styles.container}>
                                {/* <Snackbar
                                    visible={this.state.visible}
                                    onDismiss={() => this.setState({ visible: false })}
                                    action={{
                                        label: 'Undo',
                                        onPress: () => {

                                        },
                                    }}
                                >
                                    {`Item has been ${this.state.userDetails.belongsToLists.length > 0 ? 'added' : 'removed'}!`}
                                </Snackbar> */}
                                <FABGroup
                                    open={this.state.open}
                                    icon='add'
                                    color='#fff'
                                    actions={[
                                        // Add to List
                                        { 
                                            icon: this.state.userDetails.belongsToLists.length > 0 ? 'list' : 'playlist-add', 
                                            label: this.state.userDetails.belongsToLists.length > 0 ? 'Manage Tracking' : 'Add to List',
                                            onPress: () => {this.manageLists()} 
                                        },
                                        // Mark as Watched
                                        { 
                                            icon: this.state.userDetails.belongsToLists.length > 0 ? 'visibility-off' : 'visibility', 
                                            label: this.state.userDetails.belongsToLists.length > 0 ? 'Mark as Unwatched' : 'Mark as Watched',
                                            onPress: () => {this.markAsWatched()}
                                        }
                                    ]}
                                    onStateChange={({ open }) => this.setState({ open })}
                                    onPress={() => {
                                        if (this.state.open) {
                                            // do something if the speed dial is open
                                        }
                                    }}
                                    style={{marginBottom: this.state.visible ? 35 : 0 }}
                                />
                            </View>
                        </ScrollView>
                    ) }
                    
                </View>
            </PaperProvider>
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
