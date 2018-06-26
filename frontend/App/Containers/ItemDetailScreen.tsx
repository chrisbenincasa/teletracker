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
import { END } from 'redux-saga';

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
        console.tron.log(this.props.item.metadata.themoviedb);
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
            return meta.show.release_date.substring(0,4);
        } else if (this.hasTmdbPerson()) {
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

    componentDidMount() {
        MessageBarManager.registerMessageBar(this.refs.alert);
    }
    
    componentWillUnmount() {
        MessageBarManager.unregisterMessageBar();
    }
    
    render () {
        return (
            <View style={styles.container}>
                <Header
                    outerContainerStyles={headerStyles.header.outer}
                    innerContainerStyles={headerStyles.header.inner}
                    statusBarProps={headerStyles.header.statusBarProps}
                    componentId={this.props.componentId}
                    leftComponent={{icon:  'chevron-left', back: true, style: { color: 'white' } }}
                    // title={this.props.item.name}
                    centerComponent={{title: this.props.item.name,  style: { color: 'white' } }} 
                />
                <KeyboardAvoidingView behavior='position'>
                <View
                    style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: '100%',
                    }}
                >
                    <Image source={{ uri: 'https://image.tmdb.org/t/p/w500' + this.getBackdropImagePath()}} style={{
                        flex: 1, 
                        width: '100%',
                        height: '100%',
                        resizeMode: 'cover'
                    }} />
                </View>
                <View style={{
                    position: 'relative',
                    top: 60,
                    left: 0,
                    backgroundColor: 'transparent',
                    flexDirection: 'row'
                }}>
                    <Image source={{ uri: 'https://image.tmdb.org/t/p/w92' + this.getImagePath()}} style={{
                        width: 92,
                        height: 138, 
                        marginTop: 20,
                        marginLeft: 12,
                        borderWidth: 2,
                        borderColor: '#fff'
                    }} />
                    <View style={{flex: 1, flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'flex-end'}}>
                    <Text style={{marginTop: 10,marginLeft: 10,fontSize: 20}}>
                        {this.props.item.name} ({this.getReleaseYear()})
                    </Text>
                    <View style={{flexDirection: 'row'}}>
                        <Rating
                            type="star"
                            fractions={1}
                            startingValue={this.getRatingPath() / 2}
                            readonly
                            imageSize={15}
                            style={{ 
                                paddingBottom: 15,
                                marginLeft: 10
                            }}
                        />
                        <Text style={{fontSize: 10,marginLeft: 10,fontStyle: 'italic'}}>
                            ({this.getVoteCount()})
                        </Text>
                    </View>
                </View>
            </View>
        </KeyboardAvoidingView>
        <View style={{marginTop: 60}}>
            <Card>
                <Text>{this.getDescription()}</Text>
            </Card>
        </View>

        <Button title='Add' onPress={this.addItem.bind(this)}></Button>
        <Text style={{marginTop: 10, marginLeft: 12, fontSize: 16}}>Starring:</Text>
        
        <ScrollView horizontal={true} style={{flexDirection: 'row', marginTop: 5, marginLeft: 12}}>
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
            <Avatar
                large
                rounded
                source={{uri: "https://s3.amazonaws.com/uifaces/faces/twitter/kfriedson/128.jpg"}}
                activeOpacity={0.7}
                containerStyle={{paddingLeft: 5, paddingRight: 5}}
            />
        </ScrollView>
        <MessageBarAlert ref="alert" />
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
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
