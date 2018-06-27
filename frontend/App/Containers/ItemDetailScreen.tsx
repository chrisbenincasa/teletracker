import * as R from 'ramda';
import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View, ScrollView } from 'react-native';
import { Button, Card, Rating, Avatar } from 'react-native-elements';
import Header from '../Components/Header/Header';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Movie } from 'themoviedb-client-typed';
import { Navigation } from 'react-native-navigation';

import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import headerStyles from '../Themes/ApplicationStyles';
import styles from './Styles/ItemDetailScreenStyle';
import ViewMoreText from 'react-native-view-more-text';

var MessageBarAlert = require('react-native-message-bar').MessageBar;
var MessageBarManager = require('react-native-message-bar').MessageBarManager;

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    componentId: string,
    item: Thing,
    addItemToList: (componentId: string, listId: string, itemId: string | number) => any
}

class ItemDetailScreen extends Component<Props> {
    constructor(props) {
        super(props);

        // Disable menu swipe out on ItemDetailScreen
        Navigation.mergeOptions(this.props.componentId, {
            topBar: {
                transparent: true
            },
            sideMenu: {
                left: {
                    visible: false,
                    enabled: false
                }
            }
        });
    }

    private tmdbMovieView = R.lensPath(['item', 'metadata', 'themoviedb', 'movie']);

    getImagePath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
        } else if (this.hasTmdbShow()) {
            return meta.show.poster_path;
        } else if (this.hasTmdbPerson()) {
            return meta.person.profile_path;
        }
    }

    getBackdropImagePath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).backdrop_path;
        } else if (this.hasTmdbShow()) {
            return meta.show.backdrop_path; 
        } else if (this.hasTmdbPerson()) {
            return meta.person.backdrop_path; //TO DO: Confirm if this exists
        }
    }

    getRatingPath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_average;
        } else if (this.hasTmdbShow()) {
            return meta.show.vote_average;
        } else if (this.hasTmdbPerson()) {
            return meta.person.vote_average; //TO DO: Confirm if this exists
        }
    }

    getVoteCount() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_count;
        } else if (this.hasTmdbShow()) {
            return meta.show.vote_count;
        } else if (this.hasTmdbPerson()) {
            return meta.person.vote_count; //TO DO: Confirm if this exists
        }
    }

    getReleaseYear() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).release_date.substring(0,4);
        } else if (this.hasTmdbShow()) {
            return meta.show.first_air_date.substring(0,4);
        } else if (this.hasTmdbPerson()) {
            console.tron.log(meta);
            return meta.person.release_date.substring(0,4); //TO DO: Confirm if this exists
        }
    }

    getDescription() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).overview;
        } else if (this.hasTmdbShow()) {
            return meta.show.overview;
        } else if (this.hasTmdbPerson()) {
            return meta.person.overview; //TO DO: Confirm if this exists
        }
    }

    hasTmdbMetadata() {
        return this.props.item.metadata && this.props.item.metadata.themoviedb;
    }

    hasTmdbMovie() {
        return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.movie;
    }

    hasTmdbShow() {
        return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.show;
    }
  
    hasTmdbPerson() {
        return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.person;
    }

    addItem() {
        this.props.addItemToList(this.props.componentId, '???', this.props.item.id);

        MessageBarManager.showAlert({
            avatar: require('../Images/Icons/faq-icon.png'),
            title: `${this.props.item.name} has been added to your list`,
            alertType: 'success',
            position: 'bottom',
            
        });
    }

    renderViewMore(onPress) {
        return (
          <Text onPress={onPress} style={{textDecorationLine: 'underline', paddingTop: 5}}>View more</Text>
        )
      }

    renderViewLess(onPress) {
        return (
          <Text onPress={onPress} style={{textDecorationLine: 'underline', paddingTop: 5}}>View less</Text>
        )
    }

    componentDidMount() {
        MessageBarManager.registerMessageBar(this.refs.alert);
    }
    
    componentWillUnmount() {
        MessageBarManager.unregisterMessageBar();
    }
    
    render () {
        return (
            <ScrollView  style={styles.container}>
                <Header
                    outerContainerStyles={{backgroundColor: 'transparent', position: 'absolute', zIndex: 999999}}
                    statusBarProps={headerStyles.header.statusBarProps}
                    componentId={this.props.componentId}
                    leftComponent={{icon:  'chevron-left', back: true, style: { color: 'white' } }}
                    centerComponent={ null } 
                    rightComponent={ null }
                />
                <KeyboardAvoidingView behavior='position'>
                    <View style={styles.coverContainer} >
                        {   // Check if cover image exists, otherwise show blue
                            this.getBackdropImagePath() === null ? 
                            <View style={styles.emptyCoverImage}></View>
                            : <Image source={{ uri: 'https://image.tmdb.org/t/p/w500' + this.getBackdropImagePath()}} style={styles.coverImage} />
                         }
                    </View>
                    <View style={styles.subHeaderContainer}>
                        <Image source={{ uri: 'https://image.tmdb.org/t/p/w92' + this.getImagePath()}} style={styles.posterImage} />
                        <View style={styles.itemDetailsContainer}>
                            <Text style={{marginTop: 10,marginLeft: 10,fontSize: 20}}>
                                {this.props.item.name} ({this.getReleaseYear()})
                            </Text>
                            <View style={styles.ratingsContainer}>
                                <Rating
                                    type="star"
                                    fractions={1}
                                    startingValue={this.getRatingPath() / 2}
                                    readonly
                                    imageSize={15}
                                    style={{paddingBottom: 15, marginLeft: 10}}
                                />
                                <Text style={styles.ratingCount}>({this.getVoteCount()})</Text>
                            </View>
                        </View>
                    </View>
                </KeyboardAvoidingView>

                <View style={{marginTop: 60}}>
                    <Card>
                        <ViewMoreText
                            numberOfLines={6}
                            renderViewMore={this.renderViewMore}
                            renderViewLess={this.renderViewLess}
                        >
                            <Text>{this.getDescription()}</Text>
                        </ViewMoreText>
                    </Card>
                </View>

                <Button title='Add' onPress={this.addItem.bind(this)}></Button>

                <Text style={styles.castHeader}>Cast:</Text>

                {/* Static Content for now for testing */}
                <ScrollView horizontal={true} style={styles.avatarContainer}>
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                    <Avatar
                        large
                        rounded
                        source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                        activeOpacity={0.7}
                        containerStyle={styles.avatar}
                    />
                </ScrollView>
                <MessageBarAlert ref="alert" />
            </ScrollView>
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
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
