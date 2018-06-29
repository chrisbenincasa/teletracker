import * as R from 'ramda';
import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View, ScrollView } from 'react-native';
import { Button, Card, Rating, Avatar, Divider } from 'react-native-elements';
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
            return; // There are no photos for people
        }
    }

    getBackdropImagePath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).backdrop_path;
        } else if (this.hasTmdbShow()) {
            return meta.show.backdrop_path; 
        } else if (this.hasTmdbPerson()) {
            return; // There are no photos for people
        }
    }

    getRatingPath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_average;
        } else if (this.hasTmdbShow()) {
            return meta.show.vote_average;
        } else if (this.hasTmdbPerson()) {
            return; //// There is no ratings for people
        }
    }

    getVoteCount() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_count;
        } else if (this.hasTmdbShow()) {
            return meta.show.vote_count;
        } else if (this.hasTmdbPerson()) {
            return; // There is no vote count for people
        }
    }

    getReleaseYear() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).release_date.substring(0,4);
        } else if (this.hasTmdbShow()) {
            return meta.show.first_air_date.substring(0,4);
        } else if (this.hasTmdbPerson()) {
            return; // There is no release year for people
        }
    }

    getDescription() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).overview;
        } else if (this.hasTmdbShow()) {
            return meta.show.overview;
        } else if (this.hasTmdbPerson()) {
            return; // There is no description for people
        }
    }

    getCast() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).credits.cast;
        } else if (this.hasTmdbShow()) {
            return meta.show.credits.cast; 
        } else if (this.hasTmdbPerson()) {
            return; // There is no cast for people
        }
    }

    parseInitials(name) {
        var matches = name.match(/\b(\w)/g);
        return matches.join(''); 
    }

    getSeasons() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).seasons;
        } else if (this.hasTmdbShow()) {
            return meta.show.seasons; 
        } else if (this.hasTmdbPerson()) {
            return; // There are no seasons for people
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

                <View style={{marginTop: 60, marginLeft: 15, marginRight: 15}}>

                        <ViewMoreText
                            numberOfLines={6}
                            renderViewMore={this.renderViewMore}
                            renderViewLess={this.renderViewLess}
                        >
                            <Text>{this.getDescription()}</Text>
                        </ViewMoreText>
                </View>

                <Button title='Add to List' onPress={this.addItem.bind(this)} style={{backgroundColor: 'black', marginBottom: 10, marginTop: 10}}></Button>

                <Divider style={{ backgroundColor: 'grey' }} />


                {this.getSeasons() &&
                    <View>
                        <Text style={styles.castHeader}>Season Guide:</Text>
                        <ScrollView horizontal={true} style={styles.avatarContainer}>
                        {
                            this.getSeasons() ? this.getSeasons().map((i) => (
                                <View>
                                    <Avatar
                                        large
                                        rounded
                                        source={i.poster_path ? {uri: "https://image.tmdb.org/t/p/w92" + i.poster_path} : null}
                                        activeOpacity={0.7}
                                        title={i.poster_path ? null : this.parseInitials(i.name)}
                                        titleStyle={this.parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
                                    />
                                    <Text style={{width:75, textAlign: 'center', fontWeight: 'bold'}}>{i.name}</Text>
                                </View>
                            ))
                            : null
                        }
                        </ScrollView>
                    </View>
                }
                <Divider style={{ backgroundColor: 'grey' }} />
                
                {this.getCast() &&
                    <View>
                        <Text style={styles.castHeader}>Cast:</Text>

                        <ScrollView horizontal={true} style={styles.avatarContainer}>
                        {
                            this.getCast().map((i) => (
                                <View>
                                    <Avatar
                                        large
                                        rounded
                                        source={i.profile_path ? {uri: "https://image.tmdb.org/t/p/w92" + i.profile_path} : null}
                                        activeOpacity={0.7}
                                        title={i.poster_path ? null : this.parseInitials(i.name)}
                                        titleStyle={this.parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
    
                                    />
                                    <Text style={{width:75, textAlign: 'center', fontWeight: 'bold'}}>{i.name}</Text>
                                    <Text style={{width:75, textAlign: 'center', fontSize: 10, fontStyle: 'italic'}}>{i.character}</Text>
                                </View>
                            ))
                        }
                        </ScrollView>
                    </View>
                }
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
