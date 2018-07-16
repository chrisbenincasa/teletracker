import * as R from 'ramda';
import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View, ScrollView } from 'react-native';
import { Badge, Button, Card, Rating, Avatar, Divider, Icon } from 'react-native-elements';
import Header from '../Components/Header/Header';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Movie } from 'themoviedb-client-typed';
import { Navigation } from 'react-native-navigation';

import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import headerStyles from '../Themes/ApplicationStyles';
import styles from './Styles/ItemDetailScreenStyle';
import ViewMoreText from 'react-native-view-more-text';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    componentId: string,
    item: Thing,
    addItemToList: (componentId: string, listId: string, itemId: string | number) => any,
    markAsWatched: (componentId: string, itemId: string | number) => void
}

class ItemDetailScreen extends Component<Props> {
    constructor(props: Props) {
        super(props);

        this.state = {
            inList: false
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

    private tmdbMovieView = R.lensPath(['item', 'metadata', 'themoviedb', 'movie']);

    getImagePath() {
        let meta = this.props.item.metadata.themoviedb;
        console.tron.log(meta);
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
        } else if (this.hasTmdbShow()) {
            return meta.show.poster_path;
        } else if (this.hasTmdbPerson()) {
            return; // There are no photos for people yet
        }
    }

    getBackdropImagePath() {
        let meta = this.props.item.metadata.themoviedb;
        console.tron.log(meta);
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).backdrop_path;
        } else if (this.hasTmdbShow()) {
            return meta.show.backdrop_path; 
        } else if (this.hasTmdbPerson()) {
            return; // There are no backdrop photos for people yet
        }
    }

    getRatingPath() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_average;
        } else if (this.hasTmdbShow()) {
            return meta.show.vote_average;
        } else if (this.hasTmdbPerson()) {
            return; // There is no ratings for people...yet?
        }
    }

    getVoteCount() {
        // Format 11,453 as 11.4k
        const formatVoteCount = (VoteCount) => {
            return VoteCount > 9999 ? `${Math.round((VoteCount / 1000)*10)/10}k` : VoteCount;
        }

        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return formatVoteCount(R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_count);
        } else if (this.hasTmdbShow()) {
            return formatVoteCount(meta.show.vote_count);
        } else if (this.hasTmdbPerson()) {
            return; // There is no vote count for people
        }
    }

    getReleaseYear() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie() && meta.movie.release_date) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).release_date.substring(0,4);
        } else if (this.hasTmdbShow() && meta.show.first_air_date) {
            return meta.show.first_air_date.substring(0,4);
        } else if (this.hasTmdbPerson()) {
            return; // There is no release year for people, maybe birth year?
        } else {
            return null;
        }
    }

    getDescription() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).overview;
        } else if (this.hasTmdbShow()) {
            return meta.show.overview;
        } else if (this.hasTmdbPerson()) {
            return; // There is no description for people yet
        }
    }

    getCast() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).credits.cast;
        } else if (this.hasTmdbShow()) {
            return meta.show.credits.cast.length > 0 ? meta.show.credits.cast : null; 
        } else {
            return;
        }
    }

    parseInitials(name) {
        var matches = name.match(/\b(\w)/g);
        return matches.join(''); 
    }

    getSeasons() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return null; // There is no seasons parameter for movies
        } else if (this.hasTmdbShow()) {
            return meta.show.seasons; 
        } else {
            return;
        }
    }

    getGenre() {
        let meta = this.props.item.metadata.themoviedb;
        if (this.hasTmdbMovie()) {
            return R.view<Props, Movie>(this.tmdbMovieView, this.props).genres;
        } else if (this.hasTmdbShow()) {
            return meta.show.genres; 
        } else {
            return;
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
        this.props.addItemToList(this.props.componentId, 'default', this.props.item.id);

        this.setState({
            inList: !this.state.inList
          });
    }

    markAsWatched() {
        this.props.markAsWatched(this.props.componentId, this.props.item.id);
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

    render () {
        return (
            <View  style={styles.container}>
                <Header
                    outerContainerStyles={{backgroundColor: 'transparent', position: 'absolute', zIndex: 999999}}
                    statusBarProps={headerStyles.header.statusBarProps}
                    componentId={this.props.componentId}
                    leftComponent={{icon:  'chevron-left', back: true, style: { color: 'white' } }}
                    centerComponent={ null } 
                    rightComponent={ null }
                />
                <ScrollView>
                    <KeyboardAvoidingView behavior='position'>
                        <View style={styles.coverContainer} >
                            {   // Check if cover image exists, otherwise show blue
                                this.getBackdropImagePath() ? 
                                    <Image source={{ uri: 'https://image.tmdb.org/t/p/w500' + this.getBackdropImagePath()}} style={styles.coverImage} />
                                : <View style={styles.emptyCoverImage}></View> 
                            }
                        </View>
                        <View style={styles.subHeaderContainer}>
                            { this.getImagePath() ?
                                <Image 
                                    source={{ uri: 'https://image.tmdb.org/t/p/w92' + this.getImagePath()}} 
                                    style={styles.posterImage} /> : 
                                <View style={styles.posterImage}>
                                    <Icon name='image' color='#fff' size={50} containerStyle={{flex: 1}}/>
                                </View>
                            }
                            <View style={styles.itemDetailsContainer}>
                                <Text style={{marginTop: 10,marginLeft: 10,fontSize: 20}}>
                                    {this.props.item.name} 
                                    {this.getReleaseYear() ? (<Text> ({this.getReleaseYear()})</Text>) : null }
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

                    <View style={styles.descriptionContainer}>
                        <ViewMoreText
                            numberOfLines={4}
                            renderViewMore={this.renderViewMore}
                            renderViewLess={this.renderViewLess}
                        >
                            <Text>{this.getDescription()}</Text>
                        </ViewMoreText>
                    </View>

                    {this.getGenre() &&
                        <View style={styles.genreContainer}>
                            {this.getGenre() ? this.getGenre().map((i) => (
                                <Badge
                                    key={i.id}
                                    value={i.name}
                                    textStyle={{ color: 'white' }}
                                    wrapperStyle={{
                                        marginHorizontal: 2, marginVertical: 5
                                    }}
                                    containerStyle={{}}
                                />
                            )) : null}
                        </View>
                    }

                    <View style={styles.buttonsContainer}>
                        <Button 
                            title='Mark as Watched' 
                            icon={{
                                name: 'watch',
                                size: 25,
                                color: 'white'
                            }}
                            onPress={this.markAsWatched.bind(this)}
                            containerStyle={{marginHorizontal: 0}}>
                        </Button>
                        <Button 
                            title= {this.state.inList ? 'Remove'  : 'Track' }
                            onPress={this.addItem.bind(this)} 
                            icon={{
                                name: this.state.inList ? 'clear' : 'add',
                                size: 25,
                                color: 'white'
                            }}
                            buttonStyle={{
                                backgroundColor: this.state.inList ? 'red' : 'green'}}
                            containerStyle={{marginHorizontal: 0}}>
                        </Button>
                        
                    </View>

                    {this.getSeasons() &&
                        <View style={styles.seasonsContainer}>
                            <Divider style={styles.divider} />
                            <Text style={styles.seasonsHeader}>Season Guide:</Text>
                            <ScrollView horizontal={true} showsHorizontalScrollIndicator={false} style={styles.avatarContainer}>
                            {
                                this.getSeasons() ? this.getSeasons().map((i) => (
                                    <View>
                                        <Avatar
                                            key={i.id}
                                            large
                                            rounded
                                            source={i.poster_path ? {uri: "https://image.tmdb.org/t/p/w92" + i.poster_path} : null}
                                            activeOpacity={0.7}
                                            title={i.poster_path ? null : this.parseInitials(i.name)}
                                            titleStyle={this.parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
                                        />
                                        <Text style={styles.seasonsName}>{i.name}</Text>
                                    </View>
                                ))
                                : null
                            }
                            </ScrollView>
                        </View>
                    }

                    {this.getCast() &&
                        <View style={styles.castContainer}>
                            <Divider style={styles.divider} />
                            <Text style={styles.castHeader}>Cast:</Text>

                            <ScrollView horizontal={true} showsHorizontalScrollIndicator={false} style={styles.avatarContainer}>
                            {
                                this.getCast().map((i) => (
                                    <View>
                                        <Avatar
                                            key={i.id}
                                            large
                                            rounded
                                            source={i.profile_path ? {uri: "https://image.tmdb.org/t/p/w92" + i.profile_path} : null}
                                            activeOpacity={0.7}
                                            title={i.poster_path ? null : this.parseInitials(i.name)}
                                            titleStyle={this.parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
        
                                        />
                                        <Text style={styles.castName}>{i.name}</Text>
                                        <Text style={styles.castCharacter}>{i.character}</Text>
                                    </View>
                                ))
                            }
                            </ScrollView>
                        </View>
                    }
                </ScrollView>
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
        markAsWatched(componentId: string, itemId: string | number) {
            dispatch(UserActions.postEvent(componentId, 'MarkedAsWatched', 'Show', itemId));
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
